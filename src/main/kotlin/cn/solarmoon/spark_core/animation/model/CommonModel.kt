package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.anim.AnimationSet
import cn.solarmoon.spark_core.animation.anim.play.AnimPlayData
import cn.solarmoon.spark_core.animation.model.part.BonePart
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix3f
import org.joml.Matrix4f
import kotlin.collections.mutableMapOf

/**
 * 以服务端为根基的模型数据，客户端只能调用，不可以试图在客户端修改！
 */
data class CommonModel(
    val textureWidth: Int,
    val textureHeight: Int,
    val bones: LinkedHashMap<String, BonePart>
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

    /**
     * @param normal3f 法线的矩阵，从当前poseStack获取
     */
    @OnlyIn(Dist.CLIENT)
    fun renderBones(
        playData: AnimPlayData,
        matrix4f: Matrix4f,
        extraMatrix: Map<String, Matrix4f> = mapOf(),
        normal3f: Matrix3f,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
        partialTick: Float = 0f
    ) {
        bones.values.forEach {
            it.renderCubes(playData, Matrix4f(matrix4f), extraMatrix, normal3f, buffer, packedLight, packedOverlay, color, partialTick)
        }
    }

    companion object {
        @JvmStatic
        fun get(id: ResourceLocation) = ORIGINS[id] ?: EMPTY

        /**
         * 地图加载后读取的原始模型数据，最好不要修改
         */
        @JvmStatic
        val ORIGINS = linkedMapOf<ResourceLocation, CommonModel>()

        @JvmStatic
        val EMPTY get() = CommonModel(0, 0, linkedMapOf())

        @JvmStatic
        val CODEC: Codec<CommonModel> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.fieldOf("textureWidth").forGetter { it.textureWidth },
                Codec.INT.fieldOf("textureHeight").forGetter { it.textureHeight },
                BonePart.MAP_CODEC.fieldOf("bones").forGetter { it.bones }
            ).apply(it, ::CommonModel)
        }

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CommonModel::textureWidth,
            ByteBufCodecs.INT, CommonModel::textureHeight,
            BonePart.MAP_STREAM_CODEC, CommonModel::bones,
            ::CommonModel
        )

        @JvmStatic
        val ORIGIN_MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ResourceLocation.STREAM_CODEC,
            STREAM_CODEC
        )
    }

}