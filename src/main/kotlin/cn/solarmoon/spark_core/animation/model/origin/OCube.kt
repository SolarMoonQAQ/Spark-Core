package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.util.SerializeHelper
import cn.solarmoon.spark_core.util.div
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
    private val tmpM4 = Matrix4f()
    fun buildLocalNormalMatrix(): Matrix3f {
        tmpM4.identity()
            .translate(tmpPivot)
            .rotateZYX(tmpRotation)
            .translate(-tmpPivot.x, -tmpPivot.y, -tmpPivot.z)

        return tmpNormalM3.set(tmpM4).invert().transpose()
    }

    /** 用于渲染顶点的临时变量，避免大量新建对象 */
    private val tmpPivot = Vector3f()
    private val tmpRotation = Vector3f()
    private val tmpPos = Vector3f()
    private val tmpFinalNormalM3 = Matrix3f()
    private val tmpNormal = Vector3f()

    /**
     * 在客户端渲染各个顶点
     * @param force 是否跳过uv检查强制渲染,force=true时强制渲染
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
        tmpPivot.set(pivot.x, pivot.y, pivot.z)
        tmpRotation.set(rotation.x, rotation.y, rotation.z)
        matrix4f.translate(tmpPivot)
        matrix4f.rotateZYX(tmpRotation)
        matrix4f.translate(-tmpPivot.x, -tmpPivot.y, -tmpPivot.z)

        tmpFinalNormalM3.set(normal3f)
        tmpFinalNormalM3.mul(buildLocalNormalMatrix())

        for (i in polygonSet.indices) {
            val polygon = polygonSet[i]
            if (!force &&
                polygon.u1 == 0f &&
                polygon.u2 == 0f &&
                polygon.v1 == 0f &&
                polygon.v2 == 0f
            ) continue
            tmpFinalNormalM3.transform(polygon.normal, tmpNormal)
            fixInvertedFlatCube(tmpNormal)

            for (vertex in polygon.vertexes) {
                val pos = matrix4f.transformPosition(vertex.x, vertex.y, vertex.z, tmpPos)
                buffer.addVertex(
                    pos.x(), pos.y(), pos.z(), color,
                    vertex.u, vertex.v,
                    packedOverlay, packedLight,
                    tmpNormal.x, tmpNormal.y, tmpNormal.z
                )
            }
        }
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