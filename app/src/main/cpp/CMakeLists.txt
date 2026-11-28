cmake_minimum_required(VERSION 3.22)
project(nyral_js_bridge)

set(QJS_DIR quickjs)

# 裸 QuickJS：只链四个核心 C 文件，不链 quickjs-libc。
# 没有 std/os/console/module loader，脚本天然没有文件、网络、进程能力。
add_library(quickjs_core STATIC
    ${QJS_DIR}/quickjs.c
    ${QJS_DIR}/libregexp.c
    ${QJS_DIR}/libunicode.c
    ${QJS_DIR}/dtoa.c
)
target_include_directories(quickjs_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/${QJS_DIR})
target_compile_options(quickjs_core PRIVATE -O2 -fno-strict-aliasing)
set_target_properties(quickjs_core PROPERTIES C_STANDARD 11)

add_library(quickjs_bridge SHARED quickjs_bridge.cpp)
find_library(log-lib log)
target_link_libraries(quickjs_bridge quickjs_core ${log-lib})
target_compile_options(quickjs_bridge PRIVATE -O2 -std=c++17 -fno-rtti -fno-exceptions)
