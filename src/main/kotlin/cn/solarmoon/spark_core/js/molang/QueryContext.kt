package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.util.createParticleByString
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject
import org.joml.Vector3f

class QueryContext(
    val anim: AnimInstance
) {

    val animatable get() = anim.holder
    val level get() = animatable.animLevel

    @HostAccess.Export
    @JvmField
    val anim_time = anim.time.toDouble()

    @HostAccess.Export
    fun position() = animatable.getWorldPositionMatrix(1f).transformPosition(Vector3f())
    @HostAccess.Export
    fun playSound(sound: String, source: String) {
        val pos = position().toVec3().add(0.0, 1.0, 0.0)
        level?.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    @HostAccess.Export
    fun playSound(sound: String, source: String, volume: Double, pitch: Double) {
        val pos = position().toVec3().add(0.0, 1.0, 0.0)
        level?.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume.toFloat(), pitch.toFloat())
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