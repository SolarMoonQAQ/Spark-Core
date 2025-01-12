package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.phys.toRadians
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
import kotlin.collections.LinkedHashMap

data class OBone(
    val name: String,
    val parentName: String?, // parent相当于在顶部的变换，给到所有子类
    val pivot: Vec3,
    val rotation: Vec3,
    val locators: LinkedHashMap<String, OLocator>,
    val cubes: List<OCube>
) {

    /**
     * 所属模型，会在读取模型数据时放入，因此不必担心为null
     */
    var rootModel: OModel? = null

    init {
        cubes.forEach { it.rootBone = this }
    }

    /**
     * 获取当前骨骼组的父组，没有就返回null
     */
    fun getParent(): OBone? = parentName?.let { rootModel!!.getBone(it) }

    /**
     * 应用当前骨骼的变换到传入的矩阵中
     */
    fun applyTransform(
        bones: Map<String, Bone>,
        ma: Matrix4f,
        partialTick: Float = 0f,
    ): Matrix4f {
        val bone = bones[name] ?: return ma
        ma.translate(pivot.toVector3f())
        ma.translate(bone.getPosition(partialTick).toVector3f())
        ma.rotateZYX(rotation.toVector3f().add(bone.getRotation(partialTick).toVector3f()))
        ma.scale(bone.getScale(partialTick).toVector3f())
        ma.translate(pivot.toVector3f().negate())
        return ma
    }

    /**
     * 应用当前以及所有父类的骨骼的变换到传入的矩阵中
     */
    fun applyTransformWithParents(
        bones: Map<String, Bone>,
        ma: Matrix4f,
        partialTick: Float = 0f
    ): Matrix4f {
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            parent = parent.getParent()
        }

        for (i in l.asReversed()) {
            i.applyTransform(bones, ma, partialTick)
        }
        return ma
    }

    /**
     * @param normal3f 法线的矩阵，从当前poseStack获取
     */
    @OnlyIn(Dist.CLIENT)
    fun renderCubes(
        bones: Map<String, Bone>,
        ma: Matrix4f,
        normal3f: Matrix3f,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
        partialTick: Float = 0f
    ) {
        applyTransformWithParents(bones, ma, partialTick)
        cubes.forEach {
            it.renderVertexes(Matrix4f(ma), normal3f, buffer, packedLight, packedOverlay, color)
        }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<OBone> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.STRING.optionalFieldOf("parent").forGetter { Optional.ofNullable(it.parentName) },
                Vec3.CODEC.optionalFieldOf("pivot", Vec3.ZERO).forGetter { it.pivot },
                Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter { it.rotation },
                OLocator.MAP_CODEC.optionalFieldOf("locators", linkedMapOf()).forGetter { it.locators },
                OCube.LIST_CODEC.optionalFieldOf("cubes", arrayListOf()).forGetter { it.cubes },
            ).apply(it) { name, parent, pivot, rotation, locators, cubes ->
                OBone(name, parent.orElse(null), pivot, rotation, LinkedHashMap(locators), ArrayList(cubes))
            }
        }

        @JvmStatic
        val MAP_CODEC = CODEC.listOf().xmap(
            { LinkedHashMap(it.associateBy { it.name }) },
            { it.values.toList() }
        )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OBone::name,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), { Optional.ofNullable(it.parentName) },
            SerializeHelper.VEC3_STREAM_CODEC, OBone::pivot,
            SerializeHelper.VEC3_STREAM_CODEC, OBone::rotation,
            OLocator.MAP_STREAM_CODEC, OBone::locators,
            OCube.LIST_STREAM_CODEC, OBone::cubes,
            { a, b, c, d, l, e -> OBone(a, b.orElse(null), c, d, l , e)}
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.STRING_UTF8,
            STREAM_CODEC
        )
    }

}