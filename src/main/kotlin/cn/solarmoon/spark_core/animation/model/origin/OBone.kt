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
     * 判断当前骨骼是否是传入的骨骼的子代骨骼
     * @param bone 传入的骨骼
     */
    fun isChildOf(bone: OBone?): Boolean {
        if (bone == null) return false
        var isChild = false
        var parent = getParent()
        while (parent != null) {
            if (parent == bone) {
                isChild = true
                break
            }
            parent = parent.getParent()
        }
        return isChild
    }

    /**
     * 判断当前骨骼是否是传入的骨骼的子代骨骼
     * @param name 传入的骨骼的名字
     */
    fun isChildOf(name: String): Boolean = isChildOf(rootModel.getBone(name))

    /**
     * 应用当前以及所有父类的骨骼的变换到传入的矩阵中
     * @param pose 骨骼的变换数据
     * @param ma 要应用的矩阵
     * @param partialTick 插值进度
     * @param until 应用到哪个父骨骼为止，如果为null则应用至根骨骼
     */
    @JvmOverloads
    fun applyTransformWithParents(
        pose: ModelPose,
        ma: Matrix4f,
        partialTick: Float = 1f,
        until: OBone? = null
    ): Matrix4f {
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            if (parent == until) break
            parent = parent.getParent()
        }

        for (i in l.asReversed()) {
            pose.getBonePose(i.name).let { pose ->
                ma.mul(pose.getLocalTransformMatrix(partialTick))
            }
        }
        return ma
    }

    /**
     * 应用当前以及所有父类的骨骼的变换到传入的矩阵中，不考虑动画影响，仅考虑模型原始姿态
     * @param ma 要应用的矩阵
     * @param until 应用到哪个父骨骼为止，如果为null则应用至根骨骼
     */
    @JvmOverloads
    fun applyTransformWithParents(
        ma: Matrix4f,
        until: OBone? = null
    ): Matrix4f {
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            if (parent == until) break
            parent = parent.getParent()
        }

        for (bone in l.asReversed()) {
            ma.translate(bone.pivot.toVector3f())
            ma.rotateZYX(bone.rotation.toVector3f())
            ma.translate(bone.pivot.toVector3f().negate())
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
            { a, b, c, d, l, e -> OBone(a, b.orElse(null), c, d, l, e) }
        )

        @JvmStatic
        val MAP_STREAM_CODEC = ByteBufCodecs.map(
            ::LinkedHashMap,
            ByteBufCodecs.STRING_UTF8,
            STREAM_CODEC
        )
    }

}