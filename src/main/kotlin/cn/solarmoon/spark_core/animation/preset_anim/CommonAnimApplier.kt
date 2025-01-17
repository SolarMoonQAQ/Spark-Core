package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.entity.living.LivingFallEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object CommonAnimApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity

        if (entity is Player && entity.isLocalPlayer) {
            (entity as LocalPlayer).getStateMachine().processEventBlocking(PlayerStateAnimMachine.SwitchEvent())
        }

    }

    @SubscribeEvent
    private fun jump(event: LivingEvent.LivingJumpEvent) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return
        val animName = "Common/jump_start"
        val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.Common(entity, animName, CommonState.JUMP))
        entity.animController.setAnimation(event.newAnim ?: event.originAnim, 0)
    }

}