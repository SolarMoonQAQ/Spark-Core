package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import org.graalvm.polyglot.HostAccess
import org.joml.Vector3f

class QueryContext(
    val anim: AnimInstance
) {

    val animatable get() = anim.holder
    val level get() = animatable.animLevel

    @HostAccess.Export
    fun position() = animatable.getWorldPositionMatrix(1f).transformPosition(Vector3f())

    @HostAccess.Export
    fun playSound(sound: String, source: String) {
        val pos = position().toVec3()
        level?.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    @HostAccess.Export
    fun playSound(sound: String, source: String, volume: Double, pitch: Double) {
        val pos = position().toVec3()
        level?.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume.toFloat(), pitch.toFloat())
    }

}