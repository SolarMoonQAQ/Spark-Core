package cn.solarmoon.spark_core.animation

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

// 动画状态定义类
// 该类用于描述动画状态及其混合和过渡

data class AnimationStateDefinition(
    val animation: String,
    val blendSpace: Map<String, BlendSpaceEntry>,
    val transitions: Map<String, TransitionDefinition>
) {
    companion object {
        val CODEC: Codec<AnimationStateDefinition> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.STRING.fieldOf("animation").forGetter { it.animation },
                Codec.unboundedMap(Codec.STRING, BlendSpaceEntry.CODEC).fieldOf("blendSpace").forGetter { it.blendSpace },
                Codec.unboundedMap(Codec.STRING, TransitionDefinition.CODEC).fieldOf("transitions").forGetter { it.transitions }
            ).apply(builder, ::AnimationStateDefinition)
        }
    }
}

// 混合空间条目

data class BlendSpaceEntry(
    val animation: String? = null,
    val weight: Double = 1.0,
    val boneBlackList: List<String> = emptyList()
) {
    companion object {
        val CODEC: Codec<BlendSpaceEntry> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.STRING.optionalFieldOf("animation", null as String?).forGetter { it.animation },
                Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter { it.weight },
                Codec.STRING.listOf().optionalFieldOf("boneBlackList", emptyList<String>()).forGetter { it.boneBlackList }
            ).apply(builder, ::BlendSpaceEntry)
        }
    }
}

// 状态过渡定义

data class TransitionDefinition(
    val from: String,
    val to: String,
    val duration: Double,
    val blendType: String
) {
    companion object {
        val CODEC: Codec<TransitionDefinition> = RecordCodecBuilder.create { builder ->
            builder.group(
                Codec.STRING.fieldOf("from").forGetter { it.from },
                Codec.STRING.fieldOf("to").forGetter { it.to },
                Codec.DOUBLE.fieldOf("duration").forGetter { it.duration },
                Codec.STRING.fieldOf("blendType").forGetter { it.blendType }
            ).apply(builder, ::TransitionDefinition)
        }
    }
} 