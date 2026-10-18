package cn.solarmoon.spark_core.pack.diagnostic.client

import cn.solarmoon.spark_core.PackIssueNotificationMode
import cn.solarmoon.spark_core.SparkCoreConfig
import cn.solarmoon.spark_core.pack.diagnostic.PackIssueSummary
import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import cn.solarmoon.spark_core.pack.diagnostic.PackSide
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.diagnostic.event.PackAllFinishedEvent
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent

/**
 * 客户端内容包问题提示。
 *
 * 只在客户端注册：common 代码通过 [PackAllFinishedEvent] 通知，不直接引用本类。
 */
object PackIssueNotifier {

    private const val MAX_DETAIL_LINES = 10
    private const val MAX_LINES_PER_MODULE = 5

    /** 已提示过的 CLIENT 侧会话号；同一会话只提示一次。 */
    private var lastNotifiedSession = -1L

    /** 登录兜底：客户端本地包已读取完但事件途径没有提示时，这里补一次。 */
    @SubscribeEvent
    fun onLoggingIn(event: ClientPlayerNetworkEvent.LoggingIn) {
        notifyIfAny()
    }

    /** 退出时清掉本次服务器的客户端记录，避免下次登录看到旧问题。 */
    @SubscribeEvent
    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.begin(PackLoadContext.CLIENT_REMOTE)
        lastNotifiedSession = -1L
    }

    /** 汇总前 Hook：LOWEST 确保其他订阅者先补报问题。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onAllFinished(event: PackAllFinishedEvent) {
        if (event.context.side == PackSide.CLIENT) notifyIfAny()
    }

    private fun notifyIfAny() {
        val mode = SparkCoreConfig.PACK_ISSUE_NOTIFICATION.get()
        if (mode == PackIssueNotificationMode.OFF) return

        val session = SparkPackIssues.sideSessionId(PackSide.CLIENT)
        if (session == lastNotifiedSession) return

        val summary = SparkPackIssues.summary(PackSide.CLIENT)
        if (summary.isEmpty) {
            lastNotifiedSession = session
            return
        }

        // 标题界面等没有 player 的时机不消耗会话，登录时再提示
        val player = Minecraft.getInstance().player ?: return
        lastNotifiedSession = session

        when (mode) {
            PackIssueNotificationMode.SUMMARY -> {
                player.displayClientMessage(summaryMessage(summary), false)
                if (summary.dropped > 0) player.displayClientMessage(truncatedMessage(summary.dropped), false)
            }
            PackIssueNotificationMode.DETAIL ->
                detailMessages(summary).forEach { player.displayClientMessage(it, false) }
            PackIssueNotificationMode.OFF -> Unit
        }
    }

    private fun summaryMessage(summary: PackIssueSummary): Component {
        val modules = summary.modules.joinToString(", ") { "${it.moduleId}(${it.issues.size})" }
        return Component.translatableWithFallback(
            "spark_core.pack.issue.summary",
            "Content pack: %s error(s), %s warning(s), %s module(s) affected: %s. See the log for details.",
            summary.errors,
            summary.warnings,
            summary.modules.size,
            modules,
        )
    }

    private fun detailMessages(summary: PackIssueSummary): List<Component> {
        val messages = mutableListOf<Component>()
        messages += Component.translatableWithFallback(
            "spark_core.pack.issue.detail.header",
            "Content pack issues (%s error(s), %s warning(s)):",
            summary.errors,
            summary.warnings,
        )

        var shown = 0
        outer@ for (module in summary.modules) {
            var perModule = 0
            for (issue in module.issues) {
                if (shown >= MAX_DETAIL_LINES) break@outer
                if (perModule >= MAX_LINES_PER_MODULE) break

                messages += Component.translatableWithFallback(
                    "spark_core.pack.issue.detail.line",
                    "[%s] %s: %s",
                    issue.moduleId,
                    issue.location,
                    issue.message,
                )
                shown++
                perModule++
            }
        }

        val remaining = summary.errors + summary.warnings - shown
        if (remaining > 0) {
            messages += Component.translatableWithFallback(
                "spark_core.pack.issue.detail.more",
                "... and %s more, see the log.",
                remaining,
            )
        }
        if (summary.dropped > 0) {
            messages += truncatedMessage(summary.dropped)
        }
        return messages
    }

    private fun truncatedMessage(dropped: Int): Component =
        Component.translatableWithFallback(
            "spark_core.pack.issue.truncated",
            "%s more issue(s) were truncated, see the log.",
            dropped,
        )
}
