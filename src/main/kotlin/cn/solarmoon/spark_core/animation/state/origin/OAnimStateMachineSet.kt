package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.animation.IAnimatable
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

data class OAnimStateMachineSet(
    val animationControllers: MutableMap<String, OAnimStateMachine>
) {

    companion object {
        val ORIGINS = mutableMapOf<ResourceLocation, OAnimStateMachineSet>()

        fun getOrEmpty(modelIndex: ResourceLocation?) = ORIGINS[modelIndex] ?: OAnimStateMachineSet(mutableMapOf())

        val CODEC: Codec<OAnimStateMachineSet> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.unboundedMap(Codec.STRING, OAnimStateMachine.CODEC).optionalFieldOf("animation_controllers", mapOf()).forGetter { it.animationControllers }
            ).apply(ins, ::OAnimStateMachineSet)
        }
    }
}