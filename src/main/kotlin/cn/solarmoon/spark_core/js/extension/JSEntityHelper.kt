package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.entity.getRelativeVector
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import kotlin.math.PI
import kotlin.math.atan2

object JSEntityHelper {

    @HostAccess.Export
    fun move(entity: Entity, move: Vec3, orientationByInput: Boolean) {
        if (entity is Player && orientationByInput && entity.isLocalPlayer) {
            val input = (entity as LocalPlayer).savedInput
            val angle = atan2(input.moveVector.y, -input.moveVector.x) - PI.toFloat() / 2
            val move = move.yRot(angle)
            entity.deltaMovement = entity.getRelativeVector(move)
        } else {
            entity.deltaMovement = entity.getRelativeVector(move)
        }
    }

    @HostAccess.Export
    fun preventLocalInput(event: MovementInputUpdateEvent) {
        val player = event.entity as LocalPlayer
        event.input.apply {
            forwardImpulse = 0f
            leftImpulse = 0f
            up = false
            down = false
            left = false
            right = false
            jumping = false
            shiftKeyDown = false
            player.sprintTriggerTime = -1
            player.swinging = false
        }
    }

    @HostAccess.Export
    fun preventLocalInput(event: MovementInputUpdateEvent, consumer: Value) {
        val player = event.entity as LocalPlayer
        consumer.execute(event.input, player)
    }

    @HostAccess.Export
    fun commonAttack(attacker: Entity, target: Entity) {
        if (attacker.level().isClientSide) return
        if (target is LivingEntity) {
            if (attacker is Player) {
                attacker.attack(target)
            } else if (attacker is LivingEntity) {
                attacker.doHurtTarget(target)
            }
        }
    }

    @HostAccess.Export
    fun mobAttack(attacker: Entity, target: Entity, amount: Float) {
        if (attacker is LivingEntity) {
            target.hurt(attacker.damageSources().mobAttack(attacker), amount)
        }
    }

    @HostAccess.Export
    fun hurt(target: Entity, damageSource: DamageSource, amount: Float) {
        target.hurt(damageSource, amount)
    }

}