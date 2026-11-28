// QuickJS JNI 桥：Kotlin 与裸 QuickJS 之间的唯一通道。
//
// 沙箱属性：脚本侧只有 globalThis.nyral.invoke(cap, argsJson) 一个宿主入口，
// 参数与返回值都是字符串（约定 JSON）；桥本身不提供任何 I/O。
// 每个句柄独立 JSRuntime + JSContext，带内存上限、栈上限和超时中断。
#include <jni.h>
#include <chrono>
#include <cstring>
#include <string>
#include "quickjs/quickjs.h"

namespace {

JavaVM* g_vm = nullptr;

struct JsHandle {
    JSRuntime* rt = nullptr;
    JSContext* ctx = nullptr;
    std::chrono::steady_clock::time_point deadline{};
    long timeoutMs = 60000;
    jobject dispatcher = nullptr;    // GlobalRef：创建时注入的宿主分发器
    jmethodID dispatchMid = nullptr; // JsDispatcher.dispatch(String,String)String
};

JsHandle* handleOf(JSContext* ctx) {
    return static_cast<JsHandle*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
}

// 超时中断：JS_Eval 执行期间周期性回调，过点即停（NG 签名：int(JSContext*, void*)）。
int interruptCb(JSContext* /*ctx*/, void* opaque) {
    auto* h = static_cast<JsHandle*>(opaque);
    return std::chrono::steady_clock::now() > h->deadline;
}

// nyral.invoke(cap, argsJson) -> string | null
// 经 JNI 调 JsEngine.dispatch(cap, argsJson)，由 Kotlin 侧网关按策略执行并审计。
JSValue jsNyralInvoke(JSContext* ctx, JSValueConst /*this_val*/, int argc, JSValueConst* argv) {
    if (argc < 2 || !JS_IsString(argv[0]) || !JS_IsString(argv[1])) {
        return JS_ThrowTypeError(ctx, "invoke(cap: string, args: string)");
    }
    size_t capLen = 0, argsLen = 0;
    const char* cap = JS_ToCStringLen(ctx, &capLen, argv[0]);
    const char* args = JS_ToCStringLen(ctx, &argsLen, argv[1]);
    if (!cap || !args) return JS_EXCEPTION;

    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            JS_FreeCString(ctx, cap);
            JS_FreeCString(ctx, args);
            return JS_ThrowInternalError(ctx, "jni thread attach failed");
        }
        attached = true;
    }

    jstring jcap = env->NewStringUTF(cap);
    jstring jargs = env->NewStringUTF(args);
    JS_FreeCString(ctx, cap);
    JS_FreeCString(ctx, args);
    if (!jcap || !jargs) {
        env->DeleteLocalRef(jcap);
        env->DeleteLocalRef(jargs);
        return JS_ThrowInternalError(ctx, "jni string alloc failed");
    }

    auto* h = handleOf(ctx);
    if (!h || !h->dispatcher || !h->dispatchMid) {
        env->DeleteLocalRef(jcap);
        env->DeleteLocalRef(jargs);
        return JS_ThrowInternalError(ctx, "host dispatcher unavailable");
    }
    jstring jres = static_cast<jstring>(
        env->CallObjectMethod(h->dispatcher, h->dispatchMid, jcap, jargs));
    env->DeleteLocalRef(jcap);
    env->DeleteLocalRef(jargs);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return JS_ThrowInternalError(ctx, "host dispatch failed");
    }
    if (!jres) return JS_NULL; // 网关拒绝：脚本拿到 null，拿不到原因之外的东西

    const char* res = env->GetStringUTFChars(jres, nullptr);
    JSValue out = JS_NewString(ctx, res);
    env->ReleaseStringUTFChars(jres, res);
    env->DeleteLocalRef(jres);
    return out;
}

void registerNyral(JSContext* ctx) {
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue nyral = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, nyral, "invoke", JS_NewCFunction(ctx, jsNyralInvoke, "invoke", 2));
    JS_SetPropertyStr(ctx, global, "nyral", nyral);
    JS_FreeValue(ctx, global);
}

jstring evalInternal(JNIEnv* env, JsHandle* h, jstring code) {
    const char* codeStr = env->GetStringUTFChars(code, nullptr);
    if (!codeStr) return env->NewStringUTF("ERROR: string alloc failed");
    const std::string src(codeStr);
    env->ReleaseStringUTFChars(code, codeStr);

    h->deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(h->timeoutMs);
    JSValue result = JS_Eval(h->ctx, src.c_str(), src.size(), "script.js", JS_EVAL_TYPE_GLOBAL);
    if (!JS_IsException(result)) {
        JSValue json = JS_JSONStringify(h->ctx, result, JS_UNDEFINED, JS_UNDEFINED);
        JS_FreeValue(h->ctx, result);
        if (!JS_IsException(json)) {
            size_t len = 0;
            const char* out = JS_ToCStringLen(h->ctx, &len, json);
            jstring jout = out ? env->NewStringUTF(out) : env->NewStringUTF("null");
            if (out) JS_FreeCString(h->ctx, out);
            JS_FreeValue(h->ctx, json);
            return jout;
        }
    }
    // 异常路径：取异常消息回传，格式 "ERROR: ..."
    JSValue exc = JS_GetException(h->ctx);
    size_t len = 0;
    const char* msg = JS_IsNull(exc) || JS_IsUndefined(exc)
        ? "unknown exception"
        : JS_ToCStringLen(h->ctx, &len, exc);
    std::string out = "ERROR: " + std::string(msg ? msg : "unknown exception");
    if (msg) JS_FreeCString(h->ctx, msg);
    JS_FreeValue(h->ctx, exc);
    return env->NewStringUTF(out.c_str());
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_aixtin_nyrallite_plugins_js_JsEngine_newRuntime(
    JNIEnv* env, jobject /*thiz*/, jlong memoryLimit, jlong stackLimit, jlong timeoutMs,
    jobject dispatcher) {
    auto* h = new JsHandle();
    h->rt = JS_NewRuntime();
    if (!h->rt) { delete h; return 0; }
    JS_SetRuntimeOpaque(h->rt, h);
    JS_SetMemoryLimit(h->rt, static_cast<size_t>(memoryLimit));
    JS_SetMaxStackSize(h->rt, static_cast<size_t>(stackLimit));
    JS_SetInterruptHandler(h->rt, interruptCb, h);
    h->ctx = JS_NewContext(h->rt);
    if (!h->ctx) { JS_FreeRuntime(h->rt); delete h; return 0; }
    h->timeoutMs = static_cast<long>(timeoutMs);

    // 绑定本句柄的宿主分发器：JsDispatcher.dispatch(String,String)String
    jclass dcls = env->GetObjectClass(dispatcher);
    if (!dcls) { JS_FreeContext(h->ctx); JS_FreeRuntime(h->rt); delete h; return 0; }
    h->dispatchMid = env->GetMethodID(
        dcls, "dispatch", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    env->DeleteLocalRef(dcls);
    if (!h->dispatchMid) { JS_FreeContext(h->ctx); JS_FreeRuntime(h->rt); delete h; return 0; }
    h->dispatcher = env->NewGlobalRef(dispatcher);
    if (!h->dispatcher) { JS_FreeContext(h->ctx); JS_FreeRuntime(h->rt); delete h; return 0; }

    registerNyral(h->ctx);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_aixtin_nyrallite_plugins_js_JsEngine_eval(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring code) {
    auto* h = reinterpret_cast<JsHandle*>(handle);
    if (!h || !h->ctx) return env->NewStringUTF("ERROR: runtime closed");
    return evalInternal(env, h, code);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_aixtin_nyrallite_plugins_js_JsEngine_close(
    JNIEnv* env, jobject /*thiz*/, jlong handle) {
    auto* h = reinterpret_cast<JsHandle*>(handle);
    if (!h) return;
    if (h->ctx) JS_FreeContext(h->ctx);
    if (h->rt) JS_FreeRuntime(h->rt);
    if (h->dispatcher) env->DeleteGlobalRef(h->dispatcher);
    delete h;
}
