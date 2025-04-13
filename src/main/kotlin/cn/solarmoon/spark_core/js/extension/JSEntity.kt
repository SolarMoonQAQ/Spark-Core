package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.entity.getRelativeVector
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2

interface JSEntity {

    val entity get() = this as Entity

    fun commonAttack(target: Entity) {
        val entity = entity
        if (entity.level().isClientSide) return
        if (target is LivingEntity) {
            if (entity is Player) {
                entity.attack(target)
            } else if (entity is LivingEntity) {
                entity.doHurtTarget(target)
            }
        }
    }

    fun mobAttack(target: Entity, amount: Float) {
        val entity  = entity
        if (entity is LivingEntity) {
            target.hurt(entity.damageSources().mobAttack(entity), amount)
        }
    }

    fun move(move: Vec3, orientationByInput: Boolean) {
        val entity = entity
        if (entity is Player && orientationByInput && entity.isLocalPlayer) {
            val input = (entity as LocalPlayer).savedInput
            val angle = atan2(input.moveVector.y, -input.moveVector.x) - PI.toFloat() / 2
            val move = move.yRot(angle)
            entity.deltaMovement = entity.getRelativeVector(move)
        } else {
            entity.deltaMovement = entity.getRelativeVector(move)
        }
    }

    fun hurt(damageSource: DamageSource, amount: Float) {
        entity.hurt(damageSource, amount)
    }

    fun getDeltaMovement() = entity.deltaMovement

    fun getPosition() = entity.position()

    fun setCameraLock(boolean: Boolean) {
        entity.setCameraLock(boolean)
    }

    fun cameraShake(time: Int, strength: Float, frequency: Float) {
        val level = entity.level()
        if (!level.isClientSide) {
            SparkVisualEffects.CAMERA_SHAKE.shakeToClient(entity, time, strength, frequency)
        }
    }

    fun cameraShake(time: Int, strength: Float, frequency: Float, range: Double) {
        val level = entity.level()
        if (range > 0) {
            level.getEntities(entity, entity.boundingBox.inflate(range)).forEach {
                SparkVisualEffects.CAMERA_SHAKE.shakeToClient(it, time, strength, frequency)
            }
        }
    }

}