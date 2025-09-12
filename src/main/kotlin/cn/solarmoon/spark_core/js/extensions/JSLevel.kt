package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.particle.AnimatableShadowParticle
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.awt.Color

@JSClass("Level")
interface JSLevel {

    val level get() = this as Level

    fun playSound(pos: DoubleArray, sound: String, source: String) {
        level.playSound(null, pos[0], pos[1], pos[2], SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    fun playSound(pos: DoubleArray, sound: String, source: String, volume: Double, pitch: Double) {
        level.playSound(null, pos[0], pos[1], pos[2], SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume.toFloat(), pitch.toFloat())
    }

    fun playSound(pos: BlockPos, sound: String, source: String) {
        level.playSound(null, pos, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    fun playSound(pos: BlockPos, sound: String, source: String, volume: Double, pitch: Double) {
        level.playSound(null, pos, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume.toFloat(), pitch.toFloat())
    }

//    fun addParticle(animatable: IAnimatable<*>) {
//        val pos = animatable.getWorldPosition()
//        level.addParticle(AnimatableShadowParticle.Option(animatable, Color.DARK_GRAY, 15), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0)
//    }

}