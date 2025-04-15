package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.skill.SkillEvent
import com.mojang.brigadier.StringReader
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceLocation

object ParticleUtil {

    fun createParticleByString(registries: RegistryAccess, particle: String, reader: String = ""): ParticleOptions {
        val reader = StringReader(reader)
        val tag = if (reader.canRead()) TagParser(reader).readStruct() else CompoundTag()
        return BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(particle))!!.codec().codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).orThrow
    }

}