package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.anim.AnimState
import cn.solarmoon.spark_core.animation.anim.StubAnimatable
import cn.solarmoon.spark_core.animation.anim.anim
import cn.solarmoon.spark_core.animation.anim.bone
import cn.solarmoon.spark_core.animation.anim.instance
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.graph.StateTransition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * 缺陷 A 的验收用例 A-1（§4）。
 *
 * 判据是「退出节点后实例状态」：实施前实例停在 ENTER，实施后进入 EXIT。
 * activeAnimInstances 为空在修复前后都成立，不能单独作为判据，故不在此断言。
 */
class AnimStateMachineExitRecycleTest {

    /** A --leave--> B */
    private fun twoNodeGraph(): StateMachineGraph {
        val transition = StateTransition(event = "leave", target = "B", condition = StateCondition.True)
        return StateMachineGraph(
            initialNode = StateNode("A", transitions = listOf(transition)),
            nodes = listOf(StateNode("B", transitions = emptyList()))
        )
    }

    @Test
    fun a1_exitNodeRecyclesLoopingAnimInstance() {
        val holder = StubAnimatable()
        val machine = AnimStateMachine(twoNodeGraph(), holder)
        machine.start()

        val inst = instance(holder, anim("X" to bone()), 1f)
        inst.enter()
        // animLevel == null：enter() 退化为在调用线程直接处理 Start
        assertNotEquals(AnimState.IDLE, inst.state)
        assertEquals(AnimState.ENTER, inst.state)
        machine.activeAnimInstances.add(inst)

        machine.triggerEvent("leave")

        assertEquals("B", machine.currentNode.name)
        // 红→绿判据：实施前停在 ENTER，实施后为 EXIT
        assertEquals(AnimState.EXIT, inst.state)
    }

    @Test
    fun a1_multiAnimStateMachineRecyclesToo() {
        val holder = StubAnimatable()
        val machine = MultiAnimStateMachine(
            graph = twoNodeGraph(),
            animTargets = emptyList(),
            contextProvider = { throw IllegalStateException("本用例不涉及 MoLang") }
        )
        machine.start()

        val inst = instance(holder, anim("X" to bone()), 1f)
        inst.enter()
        machine.activeAnimInstances.add(inst)

        machine.triggerEvent("leave")

        assertEquals("B", machine.currentNode.name)
        assertEquals(AnimState.EXIT, inst.state)
    }
}
