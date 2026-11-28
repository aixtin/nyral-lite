;; echo_tool.wat —— Nyral Lite WASM 插件最小示例：把模型传入的参数原样回显。
;; 构建：wat2wasm --enable-annotations echo_tool.wat -o echo_tool.wasm
(module
  ;; ── 工具元数据：安装期硬校验，模型侧工具清单从这里来 ──
  (@custom "nyral.tool"
    "{\"name\":\"echo\",\"description\":\"把调用参数原样返回（文档示例）\",\"parameters\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\",\"description\":\"要回显的文本\"}},\"required\":[\"text\"]},\"entry\":\"tool_call\"}")

  ;; ── 能力导入：声明了才会出现在安装确认弹窗；未声明的能力在模块层面不存在 ──
  (import "nyral" "log" (func $log (param i32 i32)))
  (import "nyral" "get_args" (func $get_args (param i32) (result i32)))
  (import "nyral" "put_result" (func $put_result (param i32 i32) (result i32)))

  ;; ── 线性内存：必须导出，是插件与宿主之间唯一的数据通道 ──
  (memory (export "memory") 1)

  ;; 偏移 0 放日志文本；偏移 1024 起是 read_file / http_fetch 的约定结果缓冲区
  (data (i32.const 0) "echo_tool called")

  (func (export "tool_call") (result i32)
    (local $n i32)
    ;; 1. 把调用参数 JSON 读进内存 1024 处，返回字节数
    (local.set $n (call $get_args (i32.const 1024)))
    ;; 2. 记一条日志（会出现在插件审计里）
    (call $log (i32.const 0) (i32.const 16))
    ;; 3. 把结果交回宿主：示例直接原样回显参数。
    ;;    真实插件在这里解析参数、执行逻辑、拼出自己的结果 JSON
    (drop (call $put_result (i32.const 1024) (local.get $n)))
    (i32.const 0)))
