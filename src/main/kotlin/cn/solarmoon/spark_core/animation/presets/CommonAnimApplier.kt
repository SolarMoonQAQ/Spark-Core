package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import cn.solarmoon.spark_core.animation.anim.state.AnimStateMachineManager
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object CommonAnimApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity

        when {
            entity is Player -> {
                // 强制玩家实体使用 PlayerStateAnimMachine
                AnimStateMachineManager.getStateMachine(entity)?.processEventBlocking(PlayerStateAnimMachine.SwitchEvent())
            }
            else -> {
                // 其他实体使用 EntityStateAnimMachine
                AnimStateMachineManager.getStateMachine(entity)?.processEventBlocking(EntityStateAnimMachine.SwitchEvent())
            }
        }

        if (entity.jumpingLag && !entity.isAboveGround(0.1)) {
            entity.jumpingLag = false
//            if (entity is IEntityAnimatable<*>) {
//                val animName = "Common/jump_land"
//                val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.Common(entity, animName, CommonState.JUMP_LAND))
//                entity.animController.blendSpace.put(BlendAnimation(entity.newAnimInstance(event.newAnim ?: event.originAnim)))
//            }
        }

    }

    @SubscribeEvent
    private fun jump(event: LivingEvent.LivingJumpEvent) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return
        entity.jumpingLag = true
        val animName = "Common/jump_start"
        val event = NeoForge.EVENT_BUS.post(ChangePresetAnimEvent.Common(entity, animName, CommonState.JUMP))
        entity.animController.setAnimation(event.newAnim ?: event.originAnim, 0)
    }

}