package cn.solarmoon.spark_core.animation.model.part

import cn.solarmoon.spark_core.animation.anim.play.AnimPlayData
import cn.solarmoon.spark_core.animation.model.CommonModel
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.util.Optional

data class BonePart(
    val name: String,
    val parentName: String?, // parent相当于在顶部的变换，给到所有子类
    val pivot: Vec3,
    val rotation: Vec3,
    val cubes: ArrayList<CubePart>
) {

    /**
     * 所属模型，会在读取模型数据时放入，因此不必担心为null
     */
    var rootModel: CommonModel? = null

    init {
        cubes.forEach { it.rootBone = this }
    }

    /**
     * 获取当前骨骼组的父组，没有就返回null
     */
    fun getParent(): BonePart? = parentName?.let { rootModel!!.getBone(it) }

    /**
     * 应用当前骨骼的变换到传入的矩阵中
     */
    fun applyTransform(
        playData: AnimPlayData,
        ma: Matrix4f,
        extraMatrix: Map<String, Matrix4f> = mapOf(),
        partialTick: Float = 0f,
    ): Matrix4f {
        ma.translate(pivot.toVector3f())
        ma.translate(playData.getMixedBoneAnimPosition(name, partialTick))
        ma.rotateZYX(rotation.toVector3f().add(playData.getMixedBoneAnimRotation(name, partialTick)))
        ma.scale(playData.getMixedBoneAnimScale(name, partialTick))
        extraMatrix.forEach { eName, eMa -> if (name == eName) ma.mul(eMa) }
        ma.translate(pivot.toVector3f().mul(-1f))
        return ma
    }

    /**
     * 应用当前以及所有父类的骨骼的变换到传入的矩阵中
     */
    fun applyTransformWithParents(
        playData: AnimPlayData,
        ma: Matrix4f,
        extraMatrix: Map<String, Matrix4f> = mapOf(),
        partialTick: Float = 0f
    ): Matrix4f {
        val l = arrayListOf<BonePart>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            parent = parent.getParent()
        }

        for (i in l.asReversed()) {
            i.applyTransform(playData, ma, extraMatrix, partialTick)
        }
        return ma
    }

    /**
     * @param normal3f 法线的矩阵，从当前poseStack获取
     */
    @OnlyIn(Dist.CLIENT)
    fun renderCubes(
        playData: AnimPlayData,
        ma: Matrix4f,
        extraMatrix: Map<String, Matrix4f> = mapOf(),
        normal3f: Matrix3f,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
        partialTick: Float = 0f
    ) {
        applyTransformWithParents(playData, ma, extraMatrix, partialTick)
        cubes.forEach {
            it.renderVertexes(Matrix4f(ma), normal3f, buffer, packedLight, packedOverlay, color)
        }
    }

    fun copy(): BonePart {
        val copiedBones = ArrayList<CubePart>()
        cubes.forEach { copiedBones.add(it.copy()) }
        return BonePart(name, parentName, pivot, rotation, cubes)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<BonePart> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.STRING.optionalFieldOf("parent").forGetter { Optional.ofNullable(it.parentName) },
                Vec3.CODEC.optionalFieldOf("pivot", Vec3.ZERO).forGetter { it.pivot },
                Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter { it.rotation },
                CubePart.LIST_CODEC.optionalFieldOf("cubes", arrayListOf()).forGetter { it.cubes },
            ).apply(it) { name, parent, pivot, rotation, cubes ->
                BonePart(name, parent.orElse(null), pivot, rotation, ArrayList(cubes))
            }
        }

        @JvmStatic
        val LIST_CODEC = CODEC.listOf()

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BonePart::name,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), { Optional.ofNullable(it.parentName) },
            SerializeHelper.VEC3_STREAM_CODEC, BonePart::pivot,
            SerializeHelper.VEC3_STREAM_CODEC, BonePart::rotation,
            CubePart.LIST_STREAM_CODEC, BonePart::cubes,
            { a, b, c, d, e -> BonePart(a, b.orElse(null), c, d, e)}
        )

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { arrayListOf() })
    }

}