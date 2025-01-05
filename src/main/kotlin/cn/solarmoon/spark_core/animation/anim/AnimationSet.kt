package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.anim.part.Animation
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 动画集合，代表一个模型的所有动画
 */
data class AnimationSet(
    val animations: LinkedHashMap<String, Animation>
) {

    /**
     * 获取第一个匹配输入名字的动画
     */
    fun getAnimation(name: String) = animations[name]

    fun hasAnimation(name: String) = animations[name] != null

    companion object {
        /**
         * 如果res是玩家，自动返回玩家的动画集合
         */
        @JvmStatic
        fun get(res: ResourceLocation): AnimationSet {
            return ORIGINS[res] ?: EMPTY
        }

        @JvmStatic
        val EMPTY get() = AnimationSet(linkedMapOf())

        @JvmStatic
        var ORIGINS = linkedMapOf<ResourceLocation, AnimationSet>()

        @JvmStatic
        val CODEC: Codec<AnimationSet> = RecordCodecBuilder.create {
            it.group(
                Animation.MAP_CODEC.fieldOf("animations").forGetter { it.animations }
            ).apply(it, ::AnimationSet)
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            Animation.MAP_STREAM_CODEC, AnimationSet::animations,
            ::AnimationSet
        )

        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ResourceLocation.STREAM_CODEC,
            STREAM_CODEC
        )
    }

}
