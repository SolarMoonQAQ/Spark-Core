package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.client.ClientAnimationDataManager
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
         * 在客户端环境下，此方法会尝试从 ClientAnimationDataManager 获取数据。
         * 在服务器环境下或 ClientAnimationDataManager 中未找到时，会回退到静态的 ORIGINS Map。
         */
        @JvmStatic
        fun get(res: ResourceLocation): OAnimationSet {
            // 尝试获取Minecraft实例和level，判断是否在客户端逻辑中
            val mc = Minecraft.getInstance()
            // mc.level 可能为 null，例如在主菜单或某些早期加载阶段
            if (mc.level != null && mc.level!!.isClientSide) {
                // 客户端逻辑：从ClientAnimationDataManager获取
                return ClientAnimationDataManager.getAnimationSet(res) ?: EMPTY
            }
            // 服务器端逻辑或无法确定环境（例如，单元测试或早期初始化，或 mc.level 为 null）
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
