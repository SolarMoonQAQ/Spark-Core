package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

@JSClass("Entity")
interface JSEntity {

    val js_self get() = this as Entity

    fun mobAttack(target: Entity, amount: Float) {
        val entity  = js_self
        if (entity is LivingEntity) {
            target.hurt(entity.damageSources().mobAttack(entity), amount)
        }
    }

    fun hurt(damageSource: DamageSource, amount: Float) {
        js_self.hurt(damageSource, amount)
    }

    fun js_getPreInput() = js_self.preInput

    fun js_getDeltaMovement() = js_self.deltaMovement

    fun js_getPosition() = js_self.position()

    fun setCameraLock(boolean: Boolean) {
        js_self.setCameraLock(boolean)
    }

    fun addEffect(effect: String, duration: Int, amplifier: Int) = addEffect(effect, duration, amplifier, false)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean) = addEffect(effect, duration, amplifier, ambient, true)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean, visible: Boolean) = addEffect(effect, duration, amplifier, ambient, visible, true)

    fun addEffect(effect: String, duration: Int, amplifier: Int, ambient: Boolean, visible: Boolean, showIcon: Boolean) {
        val entity = js_self
        if (entity is LivingEntity) {
            entity.addEffect(MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effect)).get(), duration, amplifier, ambient, visible, showIcon))
        }
    }

}