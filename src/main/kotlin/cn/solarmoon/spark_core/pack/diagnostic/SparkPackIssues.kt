package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 内容包诊断的唯一容器。内部按 `context → moduleId → issues` 组织，模块是顶层键。
 *
 * 所有入口共享这一个 object；会话隔离由 [begin] 负责，`report` 永不抛出。
 */
object SparkPackIssues {

    /** 每个上下文最多保存的记录数；超出只累加 dropped。 */
    const val CAP_PER_CONTEXT = 200

    /** 每个上下文最多打印堆栈的 ERROR 数。 */
    private const val MAX_LOGGED_STACKS = 50

    private val LOGGER = RuntimeIssueLog.LOGGER

    private val frames = ThreadLocal.withInitial { ArrayDeque<PackFrame>() }

    private val buckets =
        ConcurrentHashMap<PackLoadContext, ConcurrentHashMap<String, CopyOnWriteArrayList<PackIssue>>>()
    private val counts = ConcurrentHashMap<PackLoadContext, AtomicInteger>()
    private val dropped = ConcurrentHashMap<PackLoadContext, AtomicInteger>()
    private val stackLogged = ConcurrentHashMap<PackLoadContext, AtomicInteger>()
    private val sessions = ConcurrentHashMap<PackLoadContext, AtomicLong>()
    private val sideSessions = ConcurrentHashMap<PackSide, AtomicLong>()

    /**
     * 开始一次新的加载会话：只清空该上下文，其他上下文互不影响。
     */
    @JvmStatic
    fun begin(context: PackLoadContext) {
        buckets.remove(context)
        counts[context] = AtomicInteger()
        dropped[context] = AtomicInteger()
        stackLogged[context] = AtomicInteger()
        sessions.computeIfAbsent(context) { AtomicLong() }.incrementAndGet()
        sideSessions.computeIfAbsent(context.side) { AtomicLong() }.incrementAndGet()
    }

    /**
     * 清空所有状态。仅供单元测试使用，不要在运行时调用。
     */
    @JvmStatic
    fun reset() {
        buckets.clear()
        counts.clear()
        dropped.clear()
        stackLogged.clear()
        sessions.clear()
        sideSessions.clear()
        frames.remove()
    }

    @JvmStatic
    fun <T> withFrame(frame: PackFrame, block: () -> T): T {
        val stack = frames.get()
        stack.push(frame)
        try {
            return block()
        } finally {
            stack.pop()
        }
    }

    @JvmStatic
    fun currentFrame(): PackFrame? = frames.get().peek()

    /**
     * 记录一条警告。没有帧时退化为普通日志，不抛异常。
     */
    @JvmStatic
    @JvmOverloads
    fun warn(message: Component, cause: Throwable? = null) {
        val frame = currentFrame()
        if (frame == null) {
            RuntimeIssueLog.warn(message, cause)
            return
        }
        report(frame.issue(PackSeverity.WARN, message, cause))
    }

    /**
     * 抛出一条内容错误，由加载器捕获并记录到当前帧。
     * 返回 [Nothing]，可以直接写在 `if (...) error(...)` 里。
     */
    @JvmStatic
    @JvmOverloads
    fun error(message: Component, cause: Throwable? = null): Nothing =
        throw PackContentException(message, cause)

    /**
     * 记录一条问题。永不抛出；超过上限时只累加 dropped。
     */
    @JvmStatic
    fun report(issue: PackIssue) {
        try {
            logIssue(issue)

            val count = counts.getOrPut(issue.context) { AtomicInteger() }
            if (count.incrementAndGet() > CAP_PER_CONTEXT) {
                dropped.getOrPut(issue.context) { AtomicInteger() }.incrementAndGet()
                return
            }

            buckets.getOrPut(issue.context) { ConcurrentHashMap() }
                .getOrPut(issue.moduleId) { CopyOnWriteArrayList() }
                .add(issue)
        } catch (e: Exception) {
            // 诊断系统自身不能影响加载
            try {
                LOGGER.error("记录内容包问题失败: ${issue.moduleId} ${issue.location}", e)
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun report(
        moduleId: String,
        location: String,
        severity: PackSeverity,
        message: Component,
        cause: Throwable?,
        context: PackLoadContext,
    ) = report(PackIssue(moduleId, null, location, severity, message, cause, context))

    @JvmStatic
    fun snapshot(context: PackLoadContext): List<PackIssue> =
        buckets[context]?.values?.flatten()?.sortedWith(ISSUE_ORDER) ?: emptyList()

    @JvmStatic
    fun snapshot(side: PackSide): List<PackIssue> =
        contextsOf(side).flatMap { snapshot(it) }.sortedWith(ISSUE_ORDER)

    @JvmStatic
    fun byModule(side: PackSide): List<PackModuleIssues> =
        snapshot(side)
            .groupBy { it.moduleId }
            .map { (moduleId, issues) -> PackModuleIssues(moduleId, issues) }

    @JvmStatic
    fun summary(side: PackSide): PackIssueSummary {
        val modules = byModule(side)
        return PackIssueSummary(
            modules = modules,
            errors = modules.sumOf { it.errors },
            warnings = modules.sumOf { it.warnings },
            dropped = contextsOf(side).sumOf { dropped[it]?.get() ?: 0 },
        )
    }

    @JvmStatic
    fun sessionId(context: PackLoadContext): Long = sessions[context]?.get() ?: 0L

    /** 该侧任一上下文 begin 时递增，用于“同一会话只提示一次”。 */
    @JvmStatic
    fun sideSessionId(side: PackSide): Long = sideSessions[side]?.get() ?: 0L

    /**
     * 服务端上下文读取结束时打一条按模块分组的汇总日志。
     * 客户端上下文不调用（客户端走聊天摘要）。
     */
    @JvmStatic
    fun logSummary(context: PackLoadContext) {
        if (context.side != PackSide.SERVER) return
        try {
            val summary = summary(PackSide.SERVER)
            if (summary.isEmpty) {
                LOGGER.info(
                    Component.translatableWithFallback(
                        "spark_core.pack.issue.server_clean",
                        "Content pack read finished: no issues.",
                    ).string
                )
                return
            }

            val moduleList = summary.modules.joinToString(", ") { "${it.moduleId}(${it.issues.size})" }
            LOGGER.info(
                Component.translatableWithFallback(
                    "spark_core.pack.issue.server_summary",
                    "Content pack read finished: %s module(s) with issues (%s error(s) / %s warning(s), %s truncated): %s",
                    summary.modules.size,
                    summary.errors,
                    summary.warnings,
                    summary.dropped,
                    moduleList,
                ).string
            )
        } catch (e: Exception) {
            LOGGER.error("内容包汇总日志输出失败", e)
        }
    }

    private fun contextsOf(side: PackSide): List<PackLoadContext> =
        PackLoadContext.entries.filter { it.side == side }

    private fun logIssue(issue: PackIssue) {
        val head = "[${issue.moduleId}] ${issue.location} @ ${issue.packId ?: "-"} → ${issue.message.string}"
        when (issue.severity) {
            PackSeverity.WARN -> if (issue.cause == null) LOGGER.warn(head) else LOGGER.warn(head, issue.cause)
            PackSeverity.ERROR -> {
                if (issue.cause == null || issue.cause is PackContentException) {
                    // 内容错误只打一行，不输出堆栈
                    LOGGER.error(head)
                } else {
                    val index = stackLogged.getOrPut(issue.context) { AtomicInteger() }.incrementAndGet()
                    if (index <= MAX_LOGGED_STACKS) {
                        LOGGER.error(head, issue.cause)
                    } else {
                        LOGGER.error("$head（堆栈已折叠）")
                    }
                }
            }
        }
    }

    private val ISSUE_ORDER = Comparator<PackIssue> { a, b ->
        var result = a.moduleId.compareTo(b.moduleId)
        if (result == 0) result = compareValues(a.packId, b.packId)
        if (result == 0) result = a.location.compareTo(b.location)
        if (result == 0) result = severityRank(a.severity).compareTo(severityRank(b.severity))
        result
    }

    private fun severityRank(severity: PackSeverity): Int =
        if (severity == PackSeverity.ERROR) 0 else 1
}
