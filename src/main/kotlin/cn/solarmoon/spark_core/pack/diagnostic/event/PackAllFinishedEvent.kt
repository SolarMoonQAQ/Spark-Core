package cn.solarmoon.spark_core.pack.diagnostic.event

import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import net.neoforged.bus.api.Event

/**
 * 当前 [PackLoadContext] 内所有模块的 `onFinish` 已执行完毕、服务端汇总与客户端摘要之前发布。
 *
 * 语义是“本次加载路径的所有模块处理完毕”，不是所有端/所有上下文一起完成。
 * 订阅者在这里 [cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues.report] 的记录会进本次汇总。
 * 只做通知，不取消、不修改内容；订阅者异常由加载器捕获。
 */
class PackAllFinishedEvent(
    val context: PackLoadContext,
) : Event()
