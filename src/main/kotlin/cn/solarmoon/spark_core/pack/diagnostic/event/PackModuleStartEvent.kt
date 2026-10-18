package cn.solarmoon.spark_core.pack.diagnostic.event

import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import net.neoforged.bus.api.Event

/**
 * 模块 `onStart` 之前发布。只做观察，不取消、不修改内容。
 *
 * 线程：资源重载时在 `prepare` 的后台线程，payload / 命令路径在主线程。
 */
class PackModuleStartEvent(
    val context: PackLoadContext,
    val moduleId: String,
) : Event()
