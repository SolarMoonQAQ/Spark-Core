package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateTransition
import cn.solarmoon.spark_core.state_machine.graph.conditions.MoLangCondition

/**
 * 将 Blockbench 状态转移条件解析为一次性事件或 MoLang 表达式。
 *
 * 以 {@code event.} 开头的条件会被解析为事件名，其余条件仍按 MoLang 表达式处理。
 */
sealed interface OTransitionTrigger {
    fun toTransition(target: String): StateTransition

    data class Event(val name: String) : OTransitionTrigger {
        override fun toTransition(target: String) = StateTransition(name, target, StateCondition.True)
    }

    data class Expression(val source: JSMolangValue) : OTransitionTrigger {
        override fun toTransition(target: String) = StateTransition(null, target, MoLangCondition(source))
    }

    data class Invalid(val source: JSMolangValue) : OTransitionTrigger {
        override fun toTransition(target: String): StateTransition = error(source.value)
    }

    companion object {
        private const val PREFIX = "event."
        private fun isValidName(name: String): Boolean = name.isNotEmpty() && name.split('.').all { part ->
            part.isNotEmpty() && (part.first().isLetter() || part.first() == '_') &&
                part.drop(1).all { it.isLetterOrDigit() || it == '_' }
        }
        fun from(source: JSMolangValue): OTransitionTrigger {
            val text = source.value.trim()
            if (!text.startsWith(PREFIX)) return Expression(source)
            val name = text.removePrefix(PREFIX)
            return if (isValidName(name)) Event(name) else Invalid(source)
        }
    }
}
