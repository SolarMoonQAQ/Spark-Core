package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.molang.core.value.IValue
import cn.solarmoon.spark_core.molang.core.value.MolangValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set

data class OAnimState(
    val animations: Map<String, IValue>,
    val onEntry: IValue,
    val onExit: IValue,
    val particleEffects: List<OParticleEffect>,
    val soundEffects: List<String>,
    val transitions: Map<String, IValue>,
    val blendTransition: Float,
    val blendViaShortestPath: Boolean
) {

    companion object {
        val CODEC: Codec<OAnimState> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.withAlternative(
                    Codec.unboundedMap(Codec.STRING, MolangValue.CODEC),
                    Codec.STRING.xmap(
                        { s -> mapOf(s to MolangValue.TRUE) },
                        { map -> map.keys.first() }
                    )
                ).listOf().xmap(
                    { l ->
                        val result = mutableMapOf<String, IValue>()
                        for (stringMolangValueMap in l) {
                            for ((key, value) in stringMolangValueMap) {
                                result[key] = value
                            }
                        }
                        result.toMap()
                    },
                    { listOf(it) }
                ).optionalFieldOf("animations", mapOf()).forGetter { it.animations },

                MolangValue.CODEC.optionalFieldOf("on_entry", MolangValue.ZERO).forGetter { it.onEntry },
                MolangValue.CODEC.optionalFieldOf("on_exit", MolangValue.ZERO).forGetter { it.onExit },
                OParticleEffect.CODEC.listOf().optionalFieldOf("particle_effects", listOf()).forGetter { it.particleEffects },
                Codec.STRING.listOf().optionalFieldOf("sound_effects", listOf()).forGetter { it.soundEffects },

                Codec.unboundedMap(Codec.STRING, MolangValue.CODEC).listOf().xmap(
                    { l ->
                        val map = mutableMapOf<String, IValue>()
                        for (stringMolangValueMap in l) {
                            for ((key, value) in stringMolangValueMap) {
                                map[key] = value
                            }
                        }
                        map.toMap()
                    },
                    { listOf(it) }
                ).optionalFieldOf("transitions", mapOf<String, IValue>()).forGetter { it.transitions },

                Codec.FLOAT.optionalFieldOf("blend_transition", 0f).forGetter { it.blendTransition },
                Codec.BOOL.optionalFieldOf("blend_via_shortest_path", false).forGetter { it.blendViaShortestPath }
            ).apply(ins, ::OAnimState)
        }
    }

}