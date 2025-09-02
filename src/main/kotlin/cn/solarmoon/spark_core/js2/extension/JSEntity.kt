package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.entity.addRelativeMovement
import cn.solarmoon.spark_core.entity.getRelativeVector
import cn.solarmoon.spark_core.js.toNativeArray
import cn.solarmoon.spark_core.js2.toValue
import cn.solarmoon.spark_core.js2.toVec2
import cn.solarmoon.spark_core.js2.toVec3
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import org.graalvm.polyglot.Value
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

    fun move(move: Value, orientationByInput: Boolean) {
        move(move, orientationByInput, Vec2(0f, 1f).toValue())
    }

    fun move(move: Value, orientationByInput: Boolean, default: Value) {
        val entity = entity
        val move = move.toVec3()
        if (entity is Player && orientationByInput && entity.isLocalPlayer) {
            val input = (entity as LocalPlayer).savedInput
            var moveVector = input.moveVector
            if (moveVector.length() == 0f) {
                moveVector = default.toVec2()
            }
            val angle = atan2(moveVector.y, -moveVector.x) - PI.toFloat() / 2
            val move = move.yRot(angle)
            entity.deltaMovement = entity.getRelativeVector(move)
        } else {
            entity.deltaMovement = entity.getRelativeVector(move)
        }
    }

    fun addMove(move: Value, orientationByInput: Boolean) {
        addMove(move, orientationByInput, Vec2(0f, 1f).toValue())
    }

    fun addMove(move: Value, orientationByInput: Boolean, default: Value) {
        val move = move.toVec3()
        val entity = entity
        if (entity is Player && orientationByInput && entity.isLocalPlayer) {
            val input = (entity as LocalPlayer).savedInput
            var moveVector = input.moveVector
            if (moveVector.length() == 0f) {
                moveVector = default.toVec2()
            }
            val angle = atan2(moveVector.y, -moveVector.x) - PI.toFloat() / 2
            val move = move.yRot(angle)
            entity.addDeltaMovement(entity.getRelativeVector(move))
        } else {
            entity.addDeltaMovement(entity.getRelativeVector(move))
        }
    }

    fun addRelativeMovement(relative: Value, movement: Value) = entity.addRelativeMovement(relative.toVec3(), movement.toVec3())

    fun hurt(damageSource: DamageSource, amount: Float) {
        entity.hurt(damageSource, amount)
    }

    fun getDeltaMovement() = entity.deltaMovement.toNativeArray()

    fun getPosition() = entity.position().toNativeArray()

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
            level.getEntities(null, entity.boundingBox.inflate(range)).forEach {
                SparkVisualEffects.CAMERA_SHAKE.shakeToClient(it, time, strength, frequency)
            }
        }
    }

    fun addEffect(effect: String, duration: Int, amplifier: Int) = addEffect(effect, duration, amplifier, false)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean) = addEffect(effect, duration, amplifier, ambient, true)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean, visible: Boolean) = addEffect(effect, duration, amplifier, ambient, visible, true)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean, visible: Boolean, showIcon: Boolean) {
        val entity = entity
        if (entity is LivingEntity) {
            entity.addEffect(MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effect)).get(), duration, amplifier, ambient, visible, showIcon))
        }
    }

}