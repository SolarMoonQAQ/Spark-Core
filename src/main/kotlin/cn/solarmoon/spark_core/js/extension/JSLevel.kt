package cn.solarmoon.spark_core.js.extension

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

interface JSLevel {

    val level get() = this as Level

    fun playSound(pos: Vec3, sound: String, source: String) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    fun playSound(pos: Vec3, sound: String, source: String, volume: Float, pitch: Float) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume, pitch)
    }

    fun playSound(pos: BlockPos, sound: String, source: String) {
        level.playSound(null, pos, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()))
    }

    fun playSound(pos: BlockPos, sound: String, source: String, volume: Float, pitch: Float) {
        level.playSound(null, pos, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(sound)), SoundSource.valueOf(source.uppercase()), volume, pitch)
    }

}