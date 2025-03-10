package cn.solarmoon.spark_core.animation.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import cn.solarmoon.spark_core.animation.anim.state.AnimStateMachineManager
import cn.solarmoon.spark_core.entity.isAboveGround
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.local_control.KeyEvent
import cn.solarmoon.spark_core.local_control.onEvent
import dev.kosmx.playerAnim.api.layered.PlayerAnimationFrame
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import ru.nsk.kstatemachine.statemachine.processEventBlocking

object CommonAnimApplier {

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        if (entity is IEntityAnimatable<*> && entity is LivingEntity) {
            AnimStateMachineManager.putStateMachine(entity, level, EntityStateAnimMachine.create(entity, entity, true))
        }
    }

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity

//        Minecraft.getInstance().options.keyAttack.onEvent(KeyEvent.PRESS_ONCE) {
//            Minecraft.getInstance().player?.animController?.setAnimation("sword:combo_${entity.tickCount % 2}", 0)
//            true
//        }

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