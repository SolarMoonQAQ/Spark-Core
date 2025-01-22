package cn.solarmoon.spark_core.flag

import cn.solarmoon.spark_core.event.OnPreInputExecuteEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

object FlagApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity.getFlag(SparkFlags.MOVE_INPUT_FREEZE) && entity is LivingEntity && entity !is Player) {
            entity.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 255, false, false))
        }
    }

    @SubscribeEvent
    private fun preInputExecute(event: OnPreInputExecuteEvent.Pre) {
        val entity = event.holder
        if (entity.getFlag(SparkFlags.DISABLE_PRE_INPUT)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    private fun playerAttack(event: AttackEntityEvent) {
        val player = event.entity
        if (player.getFlag(SparkFlags.DISARM)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    private fun entityUseItem(event: LivingEntityUseItemEvent.Start) {
        if (event.entity.getFlag(SparkFlags.SILENCE)) event.isCanceled = true
    }

    @SubscribeEvent
    private fun entityUseItem(event: LivingEntityUseItemEvent.Tick) {
        if (event.entity.getFlag(SparkFlags.SILENCE)) event.entity.stopUsingItem()
    }

    @JvmStatic
    fun stopHurtTarget(attacker: Entity, cir: CallbackInfoReturnable<Boolean>) {
        if (attacker.getFlag(SparkFlags.DISARM)) {
            cir.returnValue = false
        }
    }

    object Client {
        @SubscribeEvent
        private fun playerInputAttack(event: InputEvent.InteractionKeyMappingTriggered) {
            val player = Minecraft.getInstance().player ?: return
            if (event.isAttack && player.getFlag(SparkFlags.DISARM)) {
                event.setSwingHand(false)
                event.isCanceled = true
            } else if (player.getFlag(SparkFlags.SILENCE)) {
                event.setSwingHand(false)
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        private fun playerMove(event: MovementInputUpdateEvent) {
            val player = event.entity
            val input = event.input
            if (player.getFlag(SparkFlags.MOVE_INPUT_FREEZE)) {
                input.forwardImpulse = 0f
                input.leftImpulse = 0f
                input.up = false
                input.down = false
                input.left = false
                input.right = false
                input.jumping = false
                input.shiftKeyDown = false
                (player as? LocalPlayer)?.sprintTriggerTime = -1
                player.swinging = false
            }
        }
    }

}