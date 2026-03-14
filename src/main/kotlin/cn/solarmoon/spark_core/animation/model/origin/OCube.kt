package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.util.SerializeHelper
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.visual_effect.FovHelper
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Direction
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.tan

data class OCube(
    var originPos: Vec3,
    val size: Vec3,
    val pivot: Vec3,
    val rotation: Vec3,
    var inflate: Double,
    val uvUnion: OUVUnion,
    val mirror: Boolean,
    val textureWidth: Int,
    val textureHeight: Int,
) {

    /**
     * 所属骨骼，会在读取模型数据时放入，因此不必担心为null
     */
    var rootBone: OBone? = null

    val vertices = OVertexSet(originPos, size, inflate)

    val polygonSet = listOf(
        OPolygon.of(vertices, this, Direction.WEST),
        OPolygon.of(vertices, this, Direction.EAST),
        OPolygon.of(vertices, this, Direction.NORTH),
        OPolygon.of(vertices, this, Direction.SOUTH),
        OPolygon.of(vertices, this, Direction.UP),
        OPolygon.of(vertices, this, Direction.DOWN)
    )

    /**
     * 缓存的局部法线矩阵，因为 pivot 和 rotation 都是 val，所以可以在初始化时计算
     */
    private val cachedLocalNormalMatrix: Matrix3f

    init {
        val tmpM4 = Matrix4f()
        tmpM4.identity()
            .translate(pivot.toVector3f())
            .rotateZYX(rotation.toVector3f())
            .translate(pivot.toVector3f().negate())
        cachedLocalNormalMatrix = Matrix3f(tmpM4).invert().transpose()
    }

    /**
     * 获取转换到给定域的所有顶点坐标数据
     */
    fun getTransformedVertices(matrix4f: Matrix4f): List<Vector3f> {
        matrix4f.translate(pivot.toVector3f())
        matrix4f.rotateZYX(rotation.toVector3f())
        matrix4f.translate(pivot.div(-1.0).toVector3f())
        val list = arrayListOf<Vector3f>()
        for (polygon in polygonSet) {
            for (vertex in polygon.vertexes) {
                val vector3f2 = matrix4f.transformPosition(vertex.x, vertex.y, vertex.z, Vector3f())
                if (list.any { it == vector3f2 }) continue
                list.add(vector3f2)
            }
        }
        return list.toList()
    }

    fun getTransformedRotation(matrix4f: Matrix4f): Quaternionf {
        val correctedMatrix = Matrix4f(matrix4f)
        correctedMatrix.translate(pivot.toVector3f())
        correctedMatrix.rotateZYX(rotation.toVector3f())
        correctedMatrix.translate(pivot.toVector3f().negate())
        return correctedMatrix.getUnnormalizedRotation(Quaternionf())
    }

    fun getTransformedCenter(matrix4f: Matrix4f): Vector3f {
        val matrix4f = Matrix4f(matrix4f)
        matrix4f.translate(pivot.toVector3f())
        matrix4f.rotateZYX(rotation.toVector3f())
        matrix4f.translate(pivot.div(-1.0).toVector3f())
        val oCenter = originPos.toVector3f().add(size.toVector3f().mul(0.5f))
        val center = matrix4f.transformPosition(oCenter, Vector3f())
        return center
    }

    /** 临时变量，避免大量新建对象 */
    private val tmpNormalM3 = Matrix3f()
    fun buildLocalNormalMatrix(): Matrix3f {
        return tmpNormalM3.set(cachedLocalNormalMatrix)
    }

    /** 用于渲染顶点的临时变量，避免大量新建对象 */
    private val tmpPivot = Vector3f()
    private val tmpRotation = Vector3f()
    private val tmpPos = Vector3f()
    private val tmpFinalNormalM3 = Matrix3f()
    private val tmpNormal = Vector3f()
    private val tmpBoneM4 = Matrix4f()
    private val tmpBoneM3 = Matrix3f()

    /**
     * 在客户端渲染各个顶点
     * 面剔除参考自 https://github.com/TartaricAcid/TouhouLittleMaid/blob/cabcd1f3/src/main/java/com/github/tartaricacid/touhoulittlemaid/compat/sodium/SodiumGeoRenderer.java
     * @param matrix4f 经视图变换、世界变换、骨骼变换后得到的变换矩阵
     * @param normal3f 法线矩阵，用于计算顶点法线
     * @param force 是否跳过面剔除强制渲染, force=true时强制渲染
     */
    @OnlyIn(Dist.CLIENT)
    fun renderVertexes(
        matrix4f: Matrix4f,
        normal3f: Matrix3f,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
        force: Boolean = false //控制是否强制渲染
    ) {
        tmpBoneM4.set(matrix4f)
        tmpBoneM3.set(normal3f)
        
        tmpPivot.set(pivot.x, pivot.y, pivot.z)
        tmpRotation.set(rotation.x, rotation.y, rotation.z)
        tmpBoneM4.translate(tmpPivot)
        tmpBoneM4.rotateZYX(tmpRotation)
        tmpBoneM4.translate(-tmpPivot.x, -tmpPivot.y, -tmpPivot.z)
        // 计算矩阵的缩放因子平方用于剔除过小面（最大值）
        val m = tmpBoneM4
        val lenSqX = m.m00() * m.m00() + m.m10() * m.m10() + m.m20() * m.m20()
        val lenSqY = m.m01() * m.m01() + m.m11() * m.m11() + m.m21() * m.m21()
        val lenSqZ = m.m02() * m.m02() + m.m12() * m.m12() + m.m22() * m.m22()
        val maxScaleSq = maxOf(lenSqX, lenSqY, lenSqZ)
        val thresholdSq = getCurrentThresholdSq() // 考虑Fov的像素尺寸阈值
        tmpFinalNormalM3.set(tmpBoneM3)
        tmpFinalNormalM3.mul(buildLocalNormalMatrix())
        for (i in polygonSet.indices) {
            val polygon = polygonSet[i]

            // 1. 静态 UV 剔除
            if (!force &&
                polygon.u1 == 0f &&
                polygon.u2 == 0f &&
                polygon.v1 == 0f &&
                polygon.v2 == 0f
            ) continue

            // 计算视图空间法线
            tmpFinalNormalM3.transform(polygon.normal, tmpNormal)
            tmpNormal.normalize()
            fixInvertedFlatCube(tmpNormal)
            // 2. 动态背面剔除 (Backface Culling)
            if (!force && polygon.vertexes.isNotEmpty()) {
                val dotProduct: Float
                // 检测是否为正交投影 (GUI/Inventory)
                if (RenderSystem.getModelViewMatrix().m32() != 0f) {
                    // 正交模式：视线是平行的
                    // 在 View Space 中，相机看向 -Z 方向
                    // 法线在 Z 轴上的投影直接反映了它是否面向屏幕
                    // 如果点积 >= 0，说明面背对相机，直接跳过渲染该面
                    if (-tmpNormal.z >= 0f) {
                        continue
                    }
                } else {
                    // 透视模式：使用缓存的多边形中心
                    // 将中心点转换到 View Space
                    val refPos = tmpBoneM4.transformPosition(polygon.center, tmpPos)
                    val camDisSqr = refPos.lengthSquared() // 记录相机距离
                    refPos.normalize() // 归一化以便于衡量面投影面积变化
                    // 计算视线向量 (也就是 refPos 自身) 与法线的点积
                    dotProduct = refPos.x() * tmpNormal.x + refPos.y() * tmpNormal.y + refPos.z() * tmpNormal.z
                    // 如果点积 >= 0，说明面背对相机，直接跳过渲染该面
                    if (dotProduct >= 0f) {
                        continue
                    }
                    if (-dotProduct * polygon.size * polygon.size * maxScaleSq / camDisSqr < thresholdSq)
                        continue // 剔除过小的面
                }
            }
            for (vertex in polygon.vertexes) {
                val pos = tmpBoneM4.transformPosition(vertex.x, vertex.y, vertex.z, tmpPos)
                buffer.addVertex(
                    pos.x(), pos.y(), pos.z(), color,
                    vertex.u, vertex.v,
                    packedOverlay, packedLight,
                    tmpNormal.x, tmpNormal.y, tmpNormal.z
                )
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private fun getCurrentThresholdSq(): Float {
        val threshold = 1 // 面积小于1像素时不渲染
        val fov = FovHelper.fov // 垂直FOV，单位度
        val tanHalfFov = tan(Math.toRadians(fov / 2.0)).toFloat()
        val screenHeight = FovHelper.height
        val factor = (threshold * 2 * tanHalfFov) / screenHeight
        return factor * factor
    }

    private fun fixInvertedFlatCube(normal: Vector3f) {
        if (normal.x < 0 && (size.y == 0.0 || size.z == 0.0))
            normal.set(0f, 1f, 0f)

        if (normal.y < 0 && (size.x == 0.0 || size.z == 0.0))
            normal.set(0f, 1f, 0f)

        if (normal.z < 0 && (size.x == 0.0 || size.y == 0.0))
            normal.set(0f, 1f, 0f)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<OCube> = RecordCodecBuilder.create {
            it.group(
                Vec3.CODEC.optionalFieldOf("origin", Vec3.ZERO).forGetter { it.originPos },
                Vec3.CODEC.optionalFieldOf("size", Vec3.ZERO).forGetter { it.size },
                Vec3.CODEC.optionalFieldOf("pivot", Vec3.ZERO).forGetter { it.pivot },
                Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter { it.rotation },
                Codec.DOUBLE.optionalFieldOf("inflate", 0.0).forGetter { it.inflate },
                OUVUnion.CODEC.optionalFieldOf("uv", OUVUnion(Vec2.ZERO, null)).forGetter { it.uvUnion },
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter { it.mirror },
                Codec.INT.optionalFieldOf("textureWidth", 16).forGetter { it.textureWidth },
                Codec.INT.optionalFieldOf("textureHeight", 16).forGetter { it.textureHeight }
            ).apply(it, ::OCube)
        }

        @JvmStatic
        val LIST_CODEC = CODEC.listOf()

        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OCube> {
            override fun decode(buffer: FriendlyByteBuf): OCube {
                val origin = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                val size = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                val pivot = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                val rotation = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                val inflate = buffer.readDouble()
                val uv = OUVUnion.STREAM_CODEC.decode(buffer)
                val mirror = buffer.readBoolean()
                val tw = buffer.readInt()
                val th = buffer.readInt()
                return OCube(origin, size, pivot, rotation, inflate, uv, mirror, tw, th)
            }

            override fun encode(buffer: FriendlyByteBuf, value: OCube) {
                SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, value.originPos)
                SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, value.size)
                SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, value.pivot)
                SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, value.rotation)
                buffer.writeDouble(value.inflate)
                OUVUnion.STREAM_CODEC.encode(buffer, value.uvUnion)
                buffer.writeBoolean(value.mirror)
                buffer.writeInt(value.textureWidth)
                buffer.writeInt(value.textureHeight)
            }
        }

        @JvmStatic
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { mutableListOf() })
            .map({ it.toList() }, { it.toMutableList() })
    }

}