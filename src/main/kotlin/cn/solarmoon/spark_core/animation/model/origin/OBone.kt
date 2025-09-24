package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.animation.model.ModelPose
import cn.solarmoon.spark_core.util.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
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

    lateinit var rootModel: OModel

    init {
        cubes.forEach { it.rootBone = this }
        locators.forEach { it.value.bone = this }
    }

    /**
     * 获取当前骨骼组的父组，没有就返回null
     */
    fun getParent(): OBone? = parentName?.let { rootModel.getBone(it) }

    /**
     * 应用当前以及所有父类的骨骼的变换到传入的矩阵中
     */
    fun applyTransformWithParents(
        pose: ModelPose,
        ma: Matrix4f,
        partialTick: Float = 1f
    ): Matrix4f {
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            parent = parent.getParent()
        }

        for (i in l.asReversed()) {
            pose.getBonePose(i.name).let { pose ->
                ma.mul(pose.getLocalTransformMatrix(partialTick))
            }
        }
        return ma
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