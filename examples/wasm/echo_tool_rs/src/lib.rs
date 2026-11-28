//! echo_tool —— Nyral Lite WASM 插件 Rust 示例：把调用参数原样回显。
//! 构建：cargo build --target wasm32-unknown-unknown --release
#![no_std]

use core::panic::PanicInfo;

/// 工具元数据：安装期硬校验，模型侧工具清单从这里来
const META: &str = r#"{"name":"echo","description":"把调用参数原样返回（文档示例）","parameters":{"type":"object","properties":{"text":{"type":"string","description":"要回显的文本"}},"required":["text"]},"entry":"tool_call"}"#;

#[link_section = "nyral.tool"]
#[used]
static META_BYTES: [u8; META.len()] = {
    let src = META.as_bytes();
    let mut bytes = [0u8; META.len()];
    let mut i = 0;
    while i < src.len() {
        bytes[i] = src[i];
        i += 1;
    }
    bytes
};

/// 能力导入：声明了才会出现在安装确认弹窗；未声明的能力在模块层面不存在
#[link(wasm_import_module = "nyral")]
extern "C" {
    fn log(ptr: u32, len: u32);
    fn get_args(dest: u32) -> i32;
    fn put_result(src: u32, len: u32) -> i32;
}

const MSG: &[u8] = b"echo_tool called";

/// 约定：偏移 1024 起是 read_file / http_fetch 的结果缓冲区，本例避开它
const BUF: u32 = 4096;

#[no_mangle]
pub extern "C" fn tool_call() -> i32 {
    let n = unsafe { get_args(BUF) };
    unsafe { log(MSG.as_ptr() as u32, MSG.len() as u32) };
    if n > 0 {
        unsafe { put_result(BUF, n as u32) };
    }
    0
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    loop {}
}
