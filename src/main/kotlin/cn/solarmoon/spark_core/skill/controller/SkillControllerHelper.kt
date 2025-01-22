package cn.solarmoon.spark_core.skill.controller

import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.onStateExit

fun Entity.getAllSkillControllers() = getData(SparkAttachments.SKILL_CONTROLLER)

fun Entity.putSkillController(controller: SkillController<*>) = getAllSkillControllers().add(controller)

fun Entity.getSkillController() = getAllSkillControllers().sortedByDescending { it.priority }.firstOrNull { it.isAvailable() }

inline fun <reified T: SkillController<*>> Entity.getTypedSkillController(): T? {
    return getSkillController() as? T
}