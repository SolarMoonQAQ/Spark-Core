package cn.solarmoon.spark_core.state_machine.perf

import java.lang.management.ManagementFactory

/**
 * 极简测量夹具：对手动指定的代码块做「预热 → 测量」两阶段统计。
 *
 * 输出两个指标：
 * - 每次操作的纳秒数（ns/op）：吞吐成本
 * - 每次操作分配的字节数（B/op）：GC 压力。这是服务端 TPS 抖动的主因，
 *   因为它决定了「每秒产生多少垃圾 → 多久触发一次 young GC」。
 *
 * 分配量通过 [com.sun.management.ThreadMXBean] 读取当前线程的累计分配字节数差值
 * 得到，不需要 JFR / async-profiler。
 *
 * 该夹具**不追求 JMH 级别的严谨**（无死代码消除防护、无 fork、无黑盒消费），
 * 但足以在同一台机器上做「改前 vs 改后」和「1 个 vs N 个状态」的相对比较。
 */
object PerfHarness {

    private val allocBean: com.sun.management.ThreadMXBean? =
        (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
            ?.also { if (!it.isThreadAllocatedMemoryEnabled) it.isThreadAllocatedMemoryEnabled = true }

    val allocationMeasurementAvailable: Boolean get() = allocBean != null

    class Result(
        val label: String,
        val iterations: Int,
        val nsPerOp: Double,
        val bytesPerOp: Double,
        val totalMillis: Double,
    ) {
        override fun toString() = "%-46s %10.1f ns/op %9.1f B/op  (%,d ops in %.0f ms)"
            .format(label, nsPerOp, bytesPerOp, iterations, totalMillis)
    }

    /**
     * @param warmupMs 预热时长，让 JIT 完成 C2 编译
     * @param measureMs 测量时长
     * @param op 单次被测操作
     */
    fun measure(
        label: String,
        warmupMs: Long = 1_000,
        measureMs: Long = 1_000,
        op: () -> Unit,
    ): Result {
        // 预热：跑够时间也跑够次数，避免常数折叠
        val warmupEnd = System.nanoTime() + warmupMs * 1_000_000
        var warmupIters = 0
        while (System.nanoTime() < warmupEnd) {
            op(); warmupIters++
        }

        val tid = Thread.currentThread().threadId()
        val allocBefore = allocatedBytes(tid)
        val start = System.nanoTime()
        val end = start + measureMs * 1_000_000

        var iterations = 0
        while (System.nanoTime() < end) {
            op(); iterations++
        }

        val elapsed = System.nanoTime() - start
        val allocAfter = allocatedBytes(tid)

        return Result(
            label = label,
            iterations = iterations,
            nsPerOp = elapsed.toDouble() / iterations,
            bytesPerOp = (allocAfter - allocBefore).toDouble() / iterations,
            totalMillis = elapsed / 1_000_000.0,
        )
    }

    /** 跑 [rounds] 轮测量并取中位数，降低 JIT/GC 抖动带来的噪声 */
    fun measureMedian(
        label: String,
        rounds: Int = 3,
        warmupMs: Long = 800,
        measureMs: Long = 800,
        op: () -> Unit,
    ): Result {
        val results = (1..rounds).map { measure(label, warmupMs, measureMs, op) }
        val median = results.sortedBy { it.nsPerOp }[results.size / 2]
        return median
    }

    private fun allocatedBytes(threadId: Long): Long =
        allocBean?.getThreadAllocatedBytes(threadId) ?: 0L

    fun printHeader(title: String) {
        println()
        println("═".repeat(100))
        println("  $title")
        println("═".repeat(100))
    }

    fun report(results: List<Result>): String =
        results.joinToString("\n") { it.toString() }
}
