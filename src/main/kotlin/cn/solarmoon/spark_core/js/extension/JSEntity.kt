package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.entity.addRelativeMovement
import cn.solarmoon.spark_core.entity.getRelativeVector
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2

interface JSEntity {

    val entity get() = this as Entity
    // 调试
    fun commonAttack(target: Entity, currentAttackPhase: Int) {
        val entity = entity
        if (entity.level().isClientSide) return
        if (target is LivingEntity) {
            if (entity is Player) {
                println("attack on currentAttackPhase $currentAttackPhase")
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

    fun addRelativeMovement(relative: Vec3, movement: Vec3) = entity.addRelativeMovement(relative, movement)

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

    fun log(message: String) {
        SparkCore.LOGGER.info("发送消息: {}", message)
    }
}