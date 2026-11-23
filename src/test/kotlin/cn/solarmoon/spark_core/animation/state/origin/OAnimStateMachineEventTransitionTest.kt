package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.conditions.MoLangCondition
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OAnimStateMachineEventTransitionTest {
    @Test
    fun `Blockbench event markers become ordered event transitions`() {
        val graph = controllerGraph(
            listOf(
                "opening" to "event.inside_handle",
                "opening" to "event.outside_handle"
            )
        )

        val transitions = graph.initialNode.transitions
        assertEquals(listOf("inside_handle", "outside_handle"), transitions.map { it.event })
        assertTrueCondition(transitions)

        val controller = StateGraphController(graph)
        controller.start()
        controller.triggerEvent("outside_handle")
        assertEquals("opening", controller.currentNode.name)
    }

    @Test
    fun `Blockbench transition array retains duplicate targets through codec round trip`() {
        val json = JsonParser.parseString(
            "{\"transitions\":[{\"opening\":\"event.inside_handle\"},{\"opening\":\"event.outside_handle\"}]}"
        )
        val decoded = OAnimState.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow()
        assertEquals(listOf("opening", "opening"), decoded.transitions.map { it.first })

        val encoded = OAnimState.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow()
        val decodedAgain = OAnimState.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow()
        assertEquals(decoded.transitions, decodedAgain.transitions)
    }

    @Test
    fun `ordinary Blockbench conditions remain automatic MoLang transitions`() {
        val graph = controllerGraph(listOf("closed" to "q.all_animations_finished"))
        val transition = graph.initialNode.transitions.single()

        assertNull(transition.event)
        assertInstanceOf(MoLangCondition::class.java, transition.condition)
        assertEquals("q.all_animations_finished", (transition.condition as MoLangCondition).expression.value)
    }

    @Test
    fun `malformed event marker fails when compiled`() {
        val marker = OTransitionTrigger.from(JSMolangValue("event."))
        assertThrows(IllegalStateException::class.java) { marker.toTransition("opening") }
    }

    @Test
    fun `missing initial state defaults to the first declared state`() {
        val json = JsonParser.parseString("""
            {"states":{"z_first":{},"a_second":{}}}
        """.trimIndent())

        val machine = OAnimStateMachine.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow()

        assertEquals("z_first", machine.initialState)
        assertEquals("z_first", machine.toStateMachineGraph { null }.initialNode.name)
    }

    @Test
    fun `explicit initial state takes precedence over declared state order`() {
        val json = JsonParser.parseString("""
            {"initial_state":"a_second","states":{"z_first":{},"a_second":{}}}
        """.trimIndent())

        val machine = OAnimStateMachine.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow()

        assertEquals("a_second", machine.initialState)
    }

    private fun assertTrueCondition(transitions: List<cn.solarmoon.spark_core.state_machine.graph.StateTransition>) {
        transitions.forEach { assertEquals(StateCondition.True, it.condition) }
    }

    private fun controllerGraph(transitions: List<Pair<String, String>>): StateMachineGraph {
        val source = OAnimState(
            animations = emptyMap(),
            onEntry = JSMolangValue("0"),
            onExit = JSMolangValue("0"),
            particleEffects = emptyList(),
            soundEffects = emptyList(),
            transitions = transitions.map { (target, trigger) -> target to JSMolangValue(trigger) },
            blendTransition = 0f,
            blendViaShortestPath = false
        )
        return OAnimStateMachine("idle", mapOf("idle" to source, "opening" to source, "closed" to source))
            .toStateMachineGraph { null }
    }
}
