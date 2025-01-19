package cn.solarmoon.spark_core.skill

import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.state.initialChoiceState
import ru.nsk.kstatemachine.state.pseudo.UndoState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.onStateExit
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object SkillControllerApplier {

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        if (!event.level.isClientSide) entity.setSkillControllerStateMachine(createSkillControllerStateMachine(entity as ISkillControllerHolder<*>))
    }

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getSkillControllerStateMachine()?.processEventBlocking(SkillControllerSwitchEvent())
        entity.getAllSkillControllers().values.forEach { it.baseTick() }
        entity.getSkillController()?.tick()
    }

    @SubscribeEvent
    private fun onHit(event: LivingIncomingDamageEvent) {
        val entity = event.entity
        entity.getSkillController()?.onHurt(event)
    }

}