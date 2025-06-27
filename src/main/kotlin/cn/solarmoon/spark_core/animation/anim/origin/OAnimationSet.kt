package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.Minecraft
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 动画集合，代表一个模型的所有动画
 */
data class OAnimationSet(
    val animations: LinkedHashMap<String, OAnimation>
) {

    /**
     * 获取第一个匹配输入名字的动画
     */
    fun getAnimation(name: String) = animations[name]

    fun getValidAnimation(name: String) = animations[name] ?: throw NullPointerException("没有找到名为 $name 的动画")

    fun hasAnimation(name: String) = animations[name] != null

    companion object {
        /**
         * 获取动画集合。
         * 统一数据访问优先级：SparkRegistries动态注册表 > 静态ORIGINS
         * 在客户端和服务端都优先从动态注册表获取，确保数据一致性
         */
        @JvmStatic
        fun get(res: ResourceLocation): OAnimationSet {
            // 优先从动态注册表获取，在客户端和服务端都保持一致的优先级
            SparkRegistries.TYPED_ANIMATION?.let { registry ->
                // 尝试通过ResourceLocation找到对应的TypedAnimation
                registry.entrySet().forEach { entry ->
                    val typedAnimation = entry.value
                    if (typedAnimation.index.index == res) {
                        // 从TypedAnimation对应的静态ORIGINS获取OAnimationSet
                        return ORIGINS[res] ?: EMPTY
                    }
                }
            }
            
            // 回退到静态ORIGINS
            return ORIGINS[res] ?: EMPTY
        }

        @JvmStatic
        val EMPTY get() = OAnimationSet(linkedMapOf())

        @JvmStatic
        var ORIGINS = linkedMapOf<ResourceLocation, OAnimationSet>()

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
