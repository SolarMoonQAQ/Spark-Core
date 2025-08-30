package cn.solarmoon.spark_core.animation.anim.play.state_controller

import cn.solarmoon.spark_core.molang.core.value.MolangValue

data class BBAnimStateController(
    val animations: Map<String, MolangValue>,
    val onEntry: MolangValue,
    val onExit: MolangValue,
    val soundEffects: List<String>,
    val transitions: Map<String, MolangValue>,
    val blendTransition: Float,
    val blendViaShortestPath: Boolean
) {
//    companion object {
//        val CODEC: Codec<BBAnimStateController> = RecordCodecBuilder.create { ins ->
//            ins.group(
//                Codec.withAlternative(
//                    Codec.unboundedMap(Codec.STRING, MolangValue.CODEC),
//                    Codec.STRING.xmap(
//                        { s -> mapOf(s to MolangValue.parse("1")) },
//                        { map -> map.keys.first() }
//                    )
//                ).listOf().xmap(
//                    { list ->
//                        buildMap<String, MolangValue> {
//                            list.forEach { m -> putAll(m) }
//                        }
//                    },
//                    { mapOf<String, MolangValue>() } // 对应 Java List::of 的空实现，这里保留为空
//                ).optionalFieldOf("animations", mapOf()).forGetter { it.animations },
//                MolangValue.CODEC.optionalFieldOf("on_entry", MolangValue.parse("0")).forGetter { it.onEntry },
//                MolangValue.CODEC.optionalFieldOf("on_exit", MolangValue.parse("0")).forGetter { it.onExit },
//                Codec.STRING.listOf().optionalFieldOf("sound_effects", listOf()).forGetter { it.soundEffects },
//                Codec.unboundedMap(Codec.STRING, MolangValue.CODEC).listOf().xmap(
//                    { list ->
//                        buildMap<String, MolangValue> {
//                            list.forEach { m -> putAll(m) }
//                        }
//                    },
//                    { mapOf() }
//                ).optionalFieldOf("transitions", mapOf()).forGetter { it.transitions },
//                Codec.FLOAT.optionalFieldOf("blend_transition", 0F).forGetter { it.blendTransition },
//                Codec.BOOL.optionalFieldOf("blend_via_shortest_path", false).forGetter { it.blendViaShortestPath }
//            ).apply(ins, ::BBAnimStateController)
//        }
//    }
}
