package cn.solarmoon.spark_core.skill.controller

import cn.solarmoon.spark_core.event.PhysTickEvent
import cn.solarmoon.spark_core.event.SkillControllerRegisterEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object SkillControllerApplier {

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        NeoForge.EVENT_BUS.post(SkillControllerRegisterEvent.Entity(entity, entity.getAllSkillControllers()));
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
    private fun physTick(event: PhysTickEvent.Entity) {
        val entity = event.entity
        entity.getSkillController()?.physTick()
    }

    @SubscribeEvent
    private fun onHit(event: LivingIncomingDamageEvent) {
        val entity = event.entity
        entity.getSkillController()?.onHurt(event)
    }

}