package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.model.ModelIndex
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 动画集合，代表一个模型的所有动画
 */
data class OAnimationSet(
    var animations: LinkedHashMap<String, OAnimation>
) {

    /**
     * 获取第一个匹配输入名字的动画
     */
    fun getAnimation(name: String) = animations[name]

    fun getValidAnimation(name: String) = animations[name] ?: throw NullPointerException("没有找到名为 $name 的动画")

    fun hasAnimation(name: String) = animations[name] != null

    companion object{
        /**
         * 如果res是玩家，自动返回玩家的动画集合
         */
        @JvmStatic
        fun getOrEmpty(res: ModelIndex?): OAnimationSet {
            if (res == null) return EMPTY
            return ORIGINS[res] ?: EMPTY
        }

        @JvmStatic
        val EMPTY get() = OAnimationSet(linkedMapOf())

        @JvmStatic
        var ORIGINS = linkedMapOf<ModelIndex, OAnimationSet>()

        @JvmStatic
        val CODEC: Codec<OAnimationSet> = RecordCodecBuilder.create {
            it.group(
                OAnimation.MAP_CODEC.fieldOf("animations").forGetter { it.animations }
            ).apply(it, ::OAnimationSet)
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            OAnimation.MAP_STREAM_CODEC, OAnimationSet::animations,
            ::OAnimationSet
        )

        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ResourceLocation.STREAM_CODEC,
            STREAM_CODEC
        )
    }

}
