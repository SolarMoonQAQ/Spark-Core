package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.animation.IAnimatable
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix3f
import org.joml.Matrix4f

/**
 * 以服务端为根基的模型数据，客户端只能调用，不可以试图在客户端修改！
 */
data class OModel(
    val textureWidth: Int,
    val textureHeight: Int,
    val bones: LinkedHashMap<String, OBone>
) {

    init {
        bones.values.forEach { it.rootModel = this }
    }

    /**
     * 安全获取指定名称的骨骼
     * @throws NullPointerException 找不到名称为[name]的骨骼
     */
    fun getBone(name: String) = bones[name] ?: throw NullPointerException("找不到名为 $name 的骨骼。")

    fun hasBone(name: String) = bones[name] != null

    companion object {
        @JvmStatic
        fun get(id: ResourceLocation) = ORIGINS[id] ?: EMPTY

        /**
         * 地图加载后读取的原始模型数据，最好不要修改
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, OModel>()

        @JvmStatic
        val EMPTY get() = OModel(0, 0, linkedMapOf())

        @JvmStatic
        val CODEC: Codec<OModel> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.fieldOf("textureWidth").forGetter { it.textureWidth },
                Codec.INT.fieldOf("textureHeight").forGetter { it.textureHeight },
                OBone.MAP_CODEC.fieldOf("bones").forGetter { it.bones }
            ).apply(it, ::OModel)
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OModel::textureWidth,
            ByteBufCodecs.INT, OModel::textureHeight,
            OBone.MAP_STREAM_CODEC, OModel::bones,
            ::OModel
        )

        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ResourceLocation.STREAM_CODEC,
            STREAM_CODEC
        )
    }

}