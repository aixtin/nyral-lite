package io.github.aixtin.nyrallite

import android.content.Context
import io.github.aixtin.nyrallite.kernel.Kernel

/** 主体访问内核的唯一入口。主体是特权消费者，不经过插件权限视图。 */
val Context.kernel: Kernel get() = (applicationContext as App).kernel
