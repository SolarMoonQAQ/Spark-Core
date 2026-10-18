package cn.solarmoon.spark_core.pack.diagnostic.event

import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import net.neoforged.bus.api.Event

/**
 * 模块处理结束后发布（正常路径在 `onFinish` 之后；`onStart` 失败、模块被跳过时也会发布一次
 * `success = false`）。
 *
 * [success] 表示 `onStart` / `read` / `onFinish` 是否全部成功；失败详情走
 * [cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues]。
 */
class PackModuleFinishEvent(
    val context: PackLoadContext,
    val moduleId: String,
    val success: Boolean,
) : Event()
