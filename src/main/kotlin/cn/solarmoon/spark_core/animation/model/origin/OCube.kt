package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.Minecraft
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
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

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

    fun getTransformedCenter(matrix4f: Matrix4f): Vector3f {
        matrix4f.translate(pivot.toVector3f())
        matrix4f.rotateZYX(rotation.toVector3f())
        matrix4f.translate(pivot.div(-1.0).toVector3f())
        val oCenter = originPos.toVector3f().add(size.toVector3f().mul(0.5f))
        val center = matrix4f.transformPosition(oCenter, Vector3f())
        return center
    }

    /**
     * 在客户端渲染各个顶点
     */
    @OnlyIn(Dist.CLIENT)
    fun renderVertexes(matrix4f: Matrix4f, normal3f: Matrix3f, buffer: VertexConsumer, packedLight: Int, packedOverlay: Int, color: Int) {
        matrix4f.translate(pivot.toVector3f())
        matrix4f.rotateZYX(rotation.toVector3f())
        matrix4f.translate(pivot.div(-1.0).toVector3f())

        for (polygon in polygonSet) {
            val normal = normal3f.transform(polygon.normal, Vector3f())
            fixInvertedFlatCube(normal)

            for (vertex in polygon.vertexes) {
                val cam = Minecraft.getInstance().gameRenderer.mainCamera.position.toVector3f()
                val vector3f2 = matrix4f.transformPosition(vertex.x, vertex.y, vertex.z, Vector3f()).sub(cam)
                buffer.addVertex(
                    vector3f2.x(), vector3f2.y(), vector3f2.z(), color,
                    vertex.u, vertex.v,
                    packedOverlay, packedLight,
                    normal.x, normal.y, normal.z
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
        val LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.collection { mutableListOf() }).map({ it.toList() }, { it.toMutableList() })
    }

}