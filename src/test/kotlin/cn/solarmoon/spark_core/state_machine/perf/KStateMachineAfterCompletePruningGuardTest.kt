package cn.solarmoon.spark_core.state_machine.perf

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.HistoryType
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.InternalState
import ru.nsk.kstatemachine.state.historyState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.buildCreationArguments
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.transition.TransitionType
import ru.nsk.kstatemachine.transition.targetState
import java.lang.management.ManagementFactory

/**
 * `recursiveAfterTransitionComplete` 剪枝的守卫。
 *
 * 引擎在每次转移后对**整棵树**调用该回调，默认实现是空函数，只有 `UndoState`、`DefaultHistoryState`
 * 覆写；每子状态一次虚 suspend 调用实测 **176 B / 45 ns**，宽树上占一次转移的约 90%。
 * 剪枝后只下探「子树内确有覆写者」的分支。
 *
 * 覆写者由**类层次自动探测**（不依赖子类手工声明），因此这里既锁住「无覆写者时整棵子树被剪枝」，
 * 也锁住「覆写者藏在中层/深层时，整条祖先链都被标记、而旁支仍可剪枝」。
 * 回调本身的语义（undo / history 钩子仍然被调用）由 `PseudoStateSemanticsTest` 的 16 项覆盖。
 */
class KStateMachineAfterCompletePruningGuardTest {

    private object Go : Event

    private fun IState.needsCompletionCallback(): Boolean = (this as InternalState).needsAfterTransitionComplete

    @Test
    fun `没有覆写者的子树被剪枝`() {
        var nested: IState? = null
        val machine = createStdLibStateMachine(start = false) {
            initialState("a")
            nested = state("nested") {
                initialState("inner")
            }
        }

        assertFalse(machine.needsCompletionCallback(), "没有覆写者时整棵子树都不该被遍历")
        assertFalse(nested!!.needsCompletionCallback(), "内层子树同样没有覆写者")
    }

    @Test
    fun `启用 undo 的机器不被剪枝`() {
        val machine = createStdLibStateMachine(
            start = false,
            creationArguments = buildCreationArguments { isUndoEnabled = true }
        ) {
            initialState("a")
        }

        assertTrue(machine.needsCompletionCallback(), "UndoState 覆写了回调，遍历必须保留")
    }

    @Test
    fun `深层覆写者让整条祖先链都不被剪枝`() {
        var on: IState? = null
        var sub1: IState? = null
        var hist: IState? = null
        val machine = createStdLibStateMachine(start = false) {
            initialState("off")
            on = state("on") {
                sub1 = initialState("sub1")
                hist = historyState("hist", historyType = HistoryType.SHALLOW)
            }
        }

        assertTrue(machine.needsCompletionCallback(), "覆写者在机器子树内，机器层必须保留遍历")
        assertTrue(on!!.needsCompletionCallback(), "on 的子树含覆写者")
        assertTrue(hist!!.needsCompletionCallback(), "历史状态自身就是覆写者")
        assertFalse(sub1!!.needsCompletionCallback(), "sub1 子树没有覆写者，仍应可剪枝")
    }

    /**
     * 剪枝的实际收益：宽树上一次转移的分配量。
     *
     * 150 个子状态时实测：剪枝前 **29008 B/op**（150 × 176 B 的遍历），剪枝后应回落到 ~2.5 KB。
     * 上限取 8000 B/op：比剪枝前低 3.6×，比预期值高 3× 以上，只在上限意义上断言。
     */
    @Test
    fun `宽树的单次转移分配量低于剪枝前`() {
        assumeTrue(PerfHarness.allocationMeasurementAvailable, "本 JVM 不支持线程分配量统计")

        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            for (i in 1 until SIBLING_STATES) state("x$i")
            a.transitionConditionally<Go>("self") {
                type = TransitionType.EXTERNAL
                direction = { targetState(a) }
            }
        }
        machine.startBlocking()
        assertFalse(machine.needsCompletionCallback(), "该机器没有覆写者，遍历应被剪枝")

        val threadId = Thread.currentThread().threadId()
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        repeat(WARMUP_OPERATIONS) { machine.processEventBlocking(Go) }
        val allocatedBefore = bean.getThreadAllocatedBytes(threadId)
        repeat(MEASURED_OPERATIONS) { machine.processEventBlocking(Go) }
        val bytesPerOp = (bean.getThreadAllocatedBytes(threadId) - allocatedBefore).toDouble() / MEASURED_OPERATIONS

        assertTrue(
            bytesPerOp < TRANSITION_ALLOCATION_LIMIT,
            "宽树单次转移分配量 %.0f B/op 超过上限 %.0f B/op：整树遍历可能又回来了".format(
                bytesPerOp, TRANSITION_ALLOCATION_LIMIT
            ) + "（剪枝前 29008 B/op，剪枝后约 2.5 KB/op）"
        )
    }

    private companion object {
        const val SIBLING_STATES = 150
        const val WARMUP_OPERATIONS = 20_000
        const val MEASURED_OPERATIONS = 100_000
        const val TRANSITION_ALLOCATION_LIMIT = 8000.0
    }
}
