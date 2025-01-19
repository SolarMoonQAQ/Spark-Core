package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.network.PacketDistributor
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.onStateExit
import ru.nsk.kstatemachine.statemachine.onTransitionTriggered

fun Entity.getAllSkillControllers() = (this as ISkillControllerHolder<Entity>).allSkillControllers

fun Entity.getSkillController() = (this as ISkillControllerHolder<Entity>).skillController.takeIf { it?.isAvailable() == true }

inline fun <reified T: SkillController<*>> Entity.getTypedSkillController(): T? {
    return getSkillController() as? T
}

fun Entity.getSkillControllerStateMachine() = (this as ISkillControllerStateMachineHolder).stateMachine

fun Entity.setSkillControllerStateMachine(stateMachine: StateMachine) { (this as ISkillControllerStateMachineHolder).stateMachine = stateMachine }

fun createSkillControllerStateMachine(holder: ISkillControllerHolder<*>) = createStdLibStateMachine {
    val none = state("none")
    holder.allSkillControllers.values.forEach { addState(it) }

    val choice = initialChoiceState("choice") {
        holder.allSkillControllers.values
            .sortedByDescending { it.priority } // 按照优先级降序排序
            .firstOrNull { it.isAvailable() } ?: none
    }

    onStateEntry { state, trans ->
        if (holder is Entity) PacketDistributor.sendToAllPlayers(SkillControllerStatePayload(holder.id, state.name.toString()))
        holder.switchTo(state.name)
    }

    onStateExit { state, trans ->
        if (holder is Entity) PacketDistributor.sendToAllPlayers(SkillControllerStatePayload(holder.id, "null"))
        holder.switchTo(null)
    }

    transition<SkillControllerSwitchEvent> {
        targetState = choice
    }
}

class SkillControllerSwitchEvent: Event