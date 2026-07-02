package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateVariableContainer
import com.mojang.serialization.MapCodec

/**
 * 基于变量容器的硬编码条件。
 *
 * 接受一个 [predicate] lambda，直接访问 [StateVariableContainer] 进行任意比较。
 * 与 [HasTagCondition] 互补——HasTag 检查标记型 tags，本条件检查数值型 variables。
 *
 * 仅用于 Kotlin/Java 硬编码状态机，不参与 JSON 序列化。
 * codec 使用 [MapCodec.unit] 作为占位，避免嵌套在组合条件中序列化时崩溃。
 *
 * @param predicate 接收 [StateVariableContainer] 并返回布尔值的判断逻辑
 */
class CheckVariableCondition(
    val predicate: (StateVariableContainer) -> Boolean
) : StateCondition {
    override val codec = MapCodec.unit(this)
    override fun check(controller: StateGraphController) = predicate(controller.variables)
}
