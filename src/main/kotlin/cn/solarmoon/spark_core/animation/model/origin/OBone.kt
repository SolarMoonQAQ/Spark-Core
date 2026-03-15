package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.animation.model.ModelPose
import cn.solarmoon.spark_core.util.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.util.*

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
     * 应用当前以及所有父骨骼的【法线变换】到 Matrix3f
     * - 不包含平移
     * - 正确处理旋转 + scale（inverse-transpose）
     */
    @JvmOverloads
    fun applyNormalTransformWithParents(
        pose: ModelPose,
        normal: Matrix3f,
        partialTick: Float = 1f,
        until: OBone? = null
    ): Matrix3f {
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            if (parent == until) break
            parent = parent.getParent()
        }

        for (i in l.asReversed()) {
            val bonePose = pose.getBonePose(i.name)

            // 1. 拿到和 Matrix4f 完全一致的 local transform
            val m4 = bonePose.getLocalTransformMatrix(partialTick)

            // 2. 提取 normal matrix：inverse-transpose(upper-left 3x3)
            val n3 = Matrix3f(m4).invert().transpose()

            normal.mul(n3)
        }
        return normal
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
     * @param until 应用到哪个父骨骼为止(包括此父骨骼)，如果为null则应用至根骨骼
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

    val tmpM4 = Matrix4f()

    /**
     * 计算当前骨骼层级变换对某个局部位姿（如 Locator）的最终影响。
     * * 此方法计算从根骨骼到当前骨骼的【原始静态变换】链，并将其作用于传入的局部矩阵 [ma]。
     * 通常用于计算一个“长”在骨骼上的点在模型空间中的绝对位置。
     * * @param ma 局部空间下的位姿矩阵（如 Locator 的相对位姿），执行后将变为模型空间位姿
     * @param until 停止追溯的父骨骼节点（包含），若为 null 则追溯至根骨骼
     * @return 返回应用了层级变换后的模型空间矩阵 tmpM4
     */
    @JvmOverloads
    fun applyTransformToLocal(
        ma: Matrix4f,
        until: OBone? = null
    ): Matrix4f {
        tmpM4.identity()
        val l = arrayListOf<OBone>(this)
        var parent = getParent()
        while (parent != null) {
            l.add(parent)
            if (parent == until) break
            parent = parent.getParent()
        }

        for (bone in l.asReversed()) {
            if (bone.rotation.lengthSqr() < 1e-6) continue
            tmpM4.translate(bone.pivot.toVector3f())
            tmpM4.rotateZYX(bone.rotation.toVector3f())
            tmpM4.translate(bone.pivot.toVector3f().negate())
        }
        return tmpM4.mul(ma, ma)
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