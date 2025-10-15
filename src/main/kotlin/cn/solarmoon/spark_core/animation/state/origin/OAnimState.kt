package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.js.molang.JSMolangValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set

data class OAnimState(
    val animations: Map<String, JSMolangValue>,
    val onEntry: JSMolangValue,
    val onExit: JSMolangValue,
    val particleEffects: List<OParticleEffect>,
    val soundEffects: List<String>,
    val transitions: Map<String, JSMolangValue>,
    val blendTransition: Float,
    val blendViaShortestPath: Boolean
) {

    companion object {
        val CODEC: Codec<OAnimState> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.withAlternative(
                    Codec.unboundedMap(Codec.STRING, JSMolangValue.CODEC),
                    Codec.STRING.xmap(
                        { s -> mapOf(s to JSMolangValue("true")) },
                        { map -> map.keys.first() }
                    )
                ).listOf().xmap(
                    { l ->
                        val result = mutableMapOf<String, JSMolangValue>()
                        for (stringMolangValueMap in l) {
                            for ((key, value) in stringMolangValueMap) {
                                result[key] = value
                            }
                        }
                        result.toMap()
                    },
                    { listOf(it) }
                ).optionalFieldOf("animations", mapOf()).forGetter { it.animations },

                JSMolangValue.CODEC.optionalFieldOf("on_entry", JSMolangValue("0")).forGetter { it.onEntry },
                JSMolangValue.CODEC.optionalFieldOf("on_exit", JSMolangValue("0")).forGetter { it.onExit },
                OParticleEffect.CODEC.listOf().optionalFieldOf("particle_effects", listOf()).forGetter { it.particleEffects },
                Codec.STRING.listOf().optionalFieldOf("sound_effects", listOf()).forGetter { it.soundEffects },

                Codec.unboundedMap(Codec.STRING, JSMolangValue.CODEC).listOf().xmap(
                    { l ->
                        val map = mutableMapOf<String, JSMolangValue>()
                        for (stringMolangValueMap in l) {
                            for ((key, value) in stringMolangValueMap) {
                                map[key] = value
                            }
                        }
                        map.toMap()
                    },
                    { listOf(it) }
                ).optionalFieldOf("transitions", mapOf<String, JSMolangValue>()).forGetter { it.transitions },

                Codec.FLOAT.optionalFieldOf("blend_transition", 0f).forGetter { it.blendTransition },
                Codec.BOOL.optionalFieldOf("blend_via_shortest_path", false).forGetter { it.blendViaShortestPath }
            ).apply(ins, ::OAnimState)
        }
    }

}