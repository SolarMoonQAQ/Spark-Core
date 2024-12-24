package cn.solarmoon.spark_core.animation.anim.part

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * 单个动画，其中包括了该动画所需的所有骨骼变换
 */
data class Animation(
    val name: String,
    val loop: Loop,
    val baseLifeTime: Double,
    val boneAnims: ArrayList<BoneAnim>
) {

    init {
        boneAnims.forEach { it.rootAnimation = this }
    }

    fun getBoneAnim(name: String): BoneAnim? = boneAnims.firstOrNull { it.name == name }

    fun copy(): Animation {
        val list = arrayListOf<BoneAnim>()
        boneAnims.forEach { list.add(it.copy()) }
        return Animation(name, loop, baseLifeTime, list)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<Animation> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Loop.CODEC.fieldOf("loop").forGetter { it.loop },
                Codec.DOUBLE.fieldOf("base_lifetime").forGetter { it.baseLifeTime },
                BoneAnim.LIST_CODEC.fieldOf("bone_anims").forGetter { it.boneAnims }
            ).apply(it) { a, loo, b, c -> Animation(a, loo, b, ArrayList(c)) }
        }

        @JvmStatic
        val LIST_CODEC = CODEC.listOf()

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Animation::name,
            Loop.STREAM_CODEC, Animation::loop,
            ByteBufCodecs.DOUBLE, Animation::baseLifeTime,
            BoneAnim.LIST_STREAM_CODEC, Animation::boneAnims,
            ::Animation
        )

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { arrayListOf() })
    }

}
