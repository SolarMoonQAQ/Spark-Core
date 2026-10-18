package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component

/**
 * 当前正在处理的内容包位置。由加载器压栈，模块通过 [SparkPackIssues] 上报时自动带上。
 */
data class PackFrame(
    val moduleId: String,
    val packId: String?,
    val location: String,
    val context: PackLoadContext,
) {
    @JvmOverloads
    fun issue(
        severity: PackSeverity,
        message: Component,
        cause: Throwable? = null,
    ): PackIssue = PackIssue(moduleId, packId, location, severity, message, cause, context)
}
