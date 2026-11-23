package cn.solarmoon.spark_core.animation.anim.origin

import cn.solarmoon.spark_core.animation.AnimResourceKey
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

        /**
         * 按资源键查找动画集；没有对应动画集时返回 {@code null}。
         *
         * 键在同一张按 [ModelIndex] 索引的表里查，查找边界补上默认的
         * {@link ModelIndex#getFullDetailDistance()}：LOD 距离不参与资源身份。
         */
        @JvmStatic
        fun getOrNull(key: AnimResourceKey?): OAnimationSet? {
            if (key == null) return null
            return ORIGINS[ModelIndex(key.type, key.location)]
        }

        /**
         * 整表替换已发布的动画集。
         *
         * 加载器在全部文件读完后一次调用，运行期只会看到上一批或这一批，不会读到半成品；
         * 资源重载后同一键对应的对象引用会变化，动画体据此重新绑定。
         */
        @JvmStatic
        fun publish(sets: Map<ModelIndex, OAnimationSet>) {
            ORIGINS = LinkedHashMap(sets)
        }

        /** 共享的空动画集；只读，加载器不在此对象上合并文件。 */
        @JvmStatic
        val EMPTY: OAnimationSet = OAnimationSet(linkedMapOf())

        /** 已发布的动画集。加载器整表替换，不就地改写；外部只读或按测试需要临时增删。 */
        @JvmStatic
        var ORIGINS: MutableMap<ModelIndex, OAnimationSet> = linkedMapOf()

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
