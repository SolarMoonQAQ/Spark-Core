package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.util.createParticleByString
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.joml.Vector3f

class QueryContext: IMolangContext {

    var anim: AnimInstance? = null
    val animatable get() = anim!!.holder
    val level get() = animatable.animLevel

    override fun update(molang: String, anim: AnimInstance, context: Context, bindings: Value) {
        this.anim = anim
        this.anim?.let { anim_time = it.time }
        if (animatable.animatable is Entity) {
            val delta = (animatable.animatable as Entity).deltaMovement
            ground_speed = (delta.x * delta.x + delta.z * delta.z) * 400
            vertical_speed = if ((animatable.animatable as Entity).onGround()) 0.0 else delta.y * 20 + 1.568000030517578
        }
    }

    @HostAccess.Export
    @JvmField
    var anim_time = 0.0f

    @HostAccess.Export
    @JvmField
    var ground_speed = 0.0

    @HostAccess.Export
    @JvmField
    var vertical_speed = 0.0

    private val ZERO = Vector3f()
    @HostAccess.Export
    fun position() = animatable.getWorldPositionMatrix(1f).transformPosition(Vector3f())

    @HostAccess.Export
    fun playSound(sound: String, source: String) {
        val p = position().toVec3().add(0.0, 1.0, 0.0)
        val pos = doubleArrayOf(p.x, p.y, p.z)
        playSound(pos, sound, source, 1.0, 1.0)
    }

    @HostAccess.Export
    fun playSound(sound: String, source: String, volume: Double, pitch: Double) {
        val p = position().toVec3().add(0.0, 1.0, 0.0)
        val pos = doubleArrayOf(p.x, p.y, p.z)
        playSound(pos, sound, source, volume, pitch)
    }

    @HostAccess.Export
    fun playSound(pos: DoubleArray, sound: String, source: String, volume: Double, pitch: Double) {
        level?.playSound(null, pos[0], pos[1], pos[2], SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume.toFloat(), pitch.toFloat())
    }

    @HostAccess.Export
    fun addParticle(particle: String, pos: DoubleArray) {
        addParticle(particle, "", pos)
    }

    @HostAccess.Export
    fun addParticle(particle: String, reader: String, pos: DoubleArray) {
        val pos = pos.toVec3()
        level?.apply {
            addParticle(createParticleByString(registryAccess(), particle, reader), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0)
        }
    }

}