package cn.solarmoon.spark_core.state_machine.perf

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.transition.noTransition
import java.lang.management.ManagementFactory

/**
 * 事件解析的**分配量上限守卫**。
 *
 * `PerfHarness` 那类基准只打印不断言，实现被改回去也不会变红；这里把唯一有实测收益的优化
 * （`InternalState.findUniqueResolvedTransition` 的单遍扫描）变成一条会失败的守卫：
 *
 * | 实现 | 空转事件分配量（20 状态，每状态 1 条转移） |
 * |---|---|
 * | 原实现 `findTransitionsByEvent().map {}.filter {}`（3 个中间 List + N 个 Pair） | 1832 B/op |
 * | 单遍扫描（当前实现） | 1344 B/op |
 *
 * 阈值取 1650 B/op：比原实现低约 10%，比当前实现高约 23%，两端都留了余量。
 * 断言的是**上限**而不是目标值 —— 同一路径上的分配量是确定值，不会像 ns/op 那样抖动。
 */
class KStateMachineAllocationGuardTest {

    private object Miss : Event

    /** 与 `StateGraphController` 同形：每个状态恰好一条转移，事件匹配但方向是 noTransition。 */
    private fun idleMachine(stateCount: Int): StateMachine = createStdLibStateMachine(start = false) {
        val all = ArrayList<IState>()
        all += initialState("s0")
        for (i in 1 until stateCount) all += state("s$i")
        all.forEachIndexed { index, current ->
            current.transitionConditionally<Miss>("t$index") { direction = { noTransition() } }
        }
    }

    @Test
    fun `空转事件的每次分配量低于上限`() {
        assumeTrue(PerfHarness.allocationMeasurementAvailable, "本 JVM 不支持线程分配量统计")

        val machine = idleMachine(20)
        machine.startBlocking()

        val threadId = Thread.currentThread().threadId()
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        repeat(WARMUP_OPERATIONS) { machine.processEventBlocking(Miss) }
        val allocatedBefore = bean.getThreadAllocatedBytes(threadId)
        repeat(MEASURED_OPERATIONS) { machine.processEventBlocking(Miss) }
        val bytesPerOp = (bean.getThreadAllocatedBytes(threadId) - allocatedBefore).toDouble() / MEASURED_OPERATIONS

        assertTrue(
            bytesPerOp < ALLOCATION_LIMIT,
            "空转事件分配量 %.0f B/op 超过上限 %.0f B/op：事件解析可能又变回多趟分配了".format(bytesPerOp, ALLOCATION_LIMIT) +
                    "（原实现 1832 B/op，单遍扫描 1344 B/op）"
        )
    }

    private companion object {
        const val WARMUP_OPERATIONS = 50_000
        const val MEASURED_OPERATIONS = 200_000
        const val ALLOCATION_LIMIT = 1650.0
    }
}
