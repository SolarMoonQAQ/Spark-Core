package cn.solarmoon.spark_core.animation.model.origin

import cn.solarmoon.spark_core.util.SerializeHelper
import cn.solarmoon.spark_core.visual_effect.FovHelper
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * 对应于基岩版模型中的poly_mesh字段
 * 包含所有网格数据，无独立pivot/rotation（依赖所属骨骼）
 * 
 * @property normalizedUvs UV是否已归一化（0-1范围）
 * @property textureWidth 纹理宽度（像素）
 * @property textureHeight 纹理高度（像素）
 * @property polygons 所有多边形面
 */
data class OMesh(
    val normalizedUvs: Boolean,
    val textureWidth: Int,
    val textureHeight: Int,
    val polygons: List<OMeshPolygon>
) {
    /**
     * 所属骨骼，在加载时注入
     */
    var rootBone: OBone? = null
    
    /**
     * 顶点总数（统计用）
     */
    val vertexCount: Int get() = polygons.sumOf { it.vertices.size }
    
    /**
     * 多边形总数
     */
    val polygonCount: Int get() = polygons.size
    
    /**
     * 检查网格是否为空
     */
    val isEmpty: Boolean get() = polygons.isEmpty()


    /** 用于渲染顶点的临时变量，避免大量新建对象 */
    private val tmpPos = Vector3f()
    private val tmpFinalNormalM3 = Matrix3f()
    private val tmpNormal = Vector3f()
    private val tmpBoneM4 = Matrix4f()

    /**
     * 渲染网格到顶点缓冲区
     * 
     * @param matrix4f 世界变换矩阵
     * @param normal3f 法线变换矩阵
     * @param buffer 顶点缓冲区
     * @param packedLight 光照数据
     * @param packedOverlay 覆盖层数据
     * @param color 颜色
     * @param partialTick 插值进度
     * @param force 是否强制渲染（跳过剔除）
     */
    @OnlyIn(Dist.CLIENT)
    fun renderVertexes(
        matrix4f: Matrix4f,
        normal3f: Matrix3f,
        buffer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        color: Int,
        partialTick: Float,
        force: Boolean = false
    ) {
        tmpBoneM4.set(matrix4f)
        tmpFinalNormalM3.set(normal3f)
        
        // 计算矩阵缩放因子用于面剔除
        val m = tmpBoneM4
        val lenSqX = m.m00() * m.m00() + m.m10() * m.m10() + m.m20() * m.m20()
        val lenSqY = m.m01() * m.m01() + m.m11() * m.m11() + m.m21() * m.m21()
        val lenSqZ = m.m02() * m.m02() + m.m12() * m.m12() + m.m22() * m.m22()
        val maxScaleSq = maxOf(lenSqX, lenSqY, lenSqZ)
        
        if (maxScaleSq == 0f) return // 不渲染0尺寸的网格
        
        // 渲染每个多边形
        for (polygon in polygons) {
            val vertexNormal = polygon.vertices[0].normal
            tmpFinalNormalM3.transform(
                vertexNormal.x.toFloat(),
                vertexNormal.y.toFloat(),
                vertexNormal.z.toFloat(),
                tmpNormal
            )
            tmpNormal.normalize()
//            // 1. 面剔除检查
//            // 计算视图空间法线（使用第一个顶点的法线）
//            if (polygon.vertices.isNotEmpty()) {
//                // 检测是否为正交投影 (GUI/Inventory)
//                if (RenderSystem.getModelViewMatrix().m32() != 0f) {
//                    // 正交模式：视线是平行的
//                    // 在 View Space 中，相机看向 -Z 方向
//                    // 法线在 Z 轴上的投影直接反映了它是否面向屏幕
//                    if (-tmpNormal.z >= 0f) {
//                        continue // 面背对相机，剔除
//                    }
//                } else {
//                    // 透视模式：使用缓存的多边形中心
//                    // 将中心点转换到 View Space
//                    val centerPos = tmpBoneM4.transformPosition(
//                        polygon.center.x.toFloat(),
//                        polygon.center.y.toFloat(),
//                        polygon.center.z.toFloat(),
//                        tmpPos
//                    )
//                    val camDisSqr = centerPos.lengthSquared() // 记录相机距离
//                    centerPos.normalize() // 归一化以便于衡量面投影面积变化
//
//                    // 计算视线向量 (也就是 centerPos 自身) 与法线的点积
//                    val dotProduct = centerPos.x() * tmpNormal.x +
//                            centerPos.y() * tmpNormal.y +
//                            centerPos.z() * tmpNormal.z
//
//                    // 如果点积 >= 0，说明面背对相机，剔除
//                    if (dotProduct >= 0f) {
//                        continue
//                    }
//
//                    // 剔除过小的面（基于投影面积）
//                    val polygonSize = polygon.vertices.maxOfOrNull { v1 ->
//                        polygon.vertices.maxOfOrNull { v2 ->
//                            v1.position.distanceTo(v2.position).toFloat()
//                        } ?: 0f
//                    } ?: 0f
//
//                    val thresholdSq = getCurrentThresholdSq()
//                    if (-dotProduct * polygonSize * polygonSize * maxScaleSq / camDisSqr < thresholdSq) {
//                        continue // 面太小，剔除
//                    }
//                }
//            }
            // 2. 渲染多边形顶点
            for (vertex in polygon.vertices) {
                // 变换顶点位置
                val worldPos = matrix4f.transformPosition(
                    vertex.position.x.toFloat(),
                    vertex.position.y.toFloat(),
                    vertex.position.z.toFloat(),
                    tmpPos
                )

                // 添加顶点到缓冲区
                buffer.addVertex(
                    worldPos.x(), worldPos.y(), worldPos.z(), color,
                    vertex.uv.x, vertex.uv.y,
                    packedOverlay, packedLight,
                    tmpNormal.x(), tmpNormal.y(), tmpNormal.z()
                )
            }
        }
    }
    
    /**
     * 检查多边形是否应该被剔除
     * 
     * @return true表示应该剔除，false表示应该渲染
     */
    @OnlyIn(Dist.CLIENT)
    private fun shouldCullPolygon(
        polygon: OMeshPolygon,
        matrix4f: Matrix4f,
        normal3f: Matrix3f,
        maxScaleSq: Float
    ): Boolean {
        

        
        return false
    }
    
    /**
     * 获取当前基于FOV的像素尺寸阈值
     */
    @OnlyIn(Dist.CLIENT)
    private fun getCurrentThresholdSq(): Float {
        val threshold = 1 // 面积小于1像素时不渲染
        val fov = FovHelper.fov // 垂直FOV，单位度
        val tanHalfFov = kotlin.math.tan(Math.toRadians(fov / 2.0)).toFloat()
        val screenHeight = FovHelper.height
        val factor = (threshold * 2 * tanHalfFov) / screenHeight
        return factor * factor
    }
    
    companion object {
        /**
         * 从基岩版模型原始数据构建OMesh
         * 
         * @param normalizedUvs UV是否已归一化
         * @param positions 顶点位置数组 [[x, y, z], ...]
         * @param normals 顶点法线数组 [[nx, ny, nz], ...]
         * @param uvs 顶点UV数组 [[u, v], ...]
         * @param polys 多边形索引数组 [[[位置索引, 法线索引, UV索引], ...], ...]
         * @param textureWidth 纹理宽度
         * @param textureHeight 纹理高度
         * @return 构建完成的OMesh实例
         */
        fun fromRawData(
            normalizedUvs: Boolean,
            positions: List<List<Float>>,
            normals: List<List<Float>>,
            uvs: List<List<Float>>,
            polys: List<List<List<Int>>>,
            textureWidth: Int,
            textureHeight: Int
        ): OMesh {
            val polygons = polys.map { polyIndices ->
                OMeshPolygon.fromRawIndices(
                    polyIndices = polyIndices,
                    positions = positions,
                    normals = normals,
                    uvs = uvs,
                    textureWidth = textureWidth,
                    textureHeight = textureHeight,
                    normalizedUvs = normalizedUvs
                )
            }
            
            return OMesh(normalizedUvs, textureWidth, textureHeight, polygons)
        }
        
        /**
         * Codec用于JSON序列化/反序列化
         * 对应基岩版模型的poly_mesh字段格式
         */
        @JvmStatic
        val CODEC: Codec<OMesh> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("normalized_uvs", true).forGetter { it.normalizedUvs },
                Codec.INT.optionalFieldOf("texture_width", 16).forGetter { it.textureWidth },
                Codec.INT.optionalFieldOf("texture_height", 16).forGetter { it.textureHeight },
                Codec.list(Codec.list(Codec.FLOAT)).optionalFieldOf("positions", emptyList()).forGetter { emptyList() },
                Codec.list(Codec.list(Codec.FLOAT)).optionalFieldOf("normals", emptyList()).forGetter { emptyList() },
                Codec.list(Codec.list(Codec.FLOAT)).optionalFieldOf("uvs", emptyList()).forGetter { emptyList() },
                Codec.list(Codec.list(Codec.list(Codec.INT))).optionalFieldOf("polys", emptyList()).forGetter { emptyList() }
            ).apply(instance) { normalizedUvs, textureWidth, textureHeight, positions, normals, uvs, polys ->
                // 使用fromRawData方法构建OMesh实例
                fromRawData(
                    normalizedUvs = normalizedUvs,
                    positions = positions,
                    normals = normals,
                    uvs = uvs,
                    polys = polys,
                    textureWidth = textureWidth,
                    textureHeight = textureHeight
                )
            }
        }
        
        /**
         * StreamCodec用于网络同步
         */
        @JvmStatic
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, OMesh> {
            override fun decode(buffer: FriendlyByteBuf): OMesh {
                val normalizedUvs = buffer.readBoolean()
                val textureWidth = buffer.readInt()
                val textureHeight = buffer.readInt()
                val polyCount = buffer.readInt()
                
                val polygons = mutableListOf<OMeshPolygon>()
                repeat(polyCount) {
                    val vertexCount = buffer.readByte().toInt()
                    val vertices = mutableListOf<OMeshVertex>()
                    repeat(vertexCount) {
                        val pos = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                        val normal = SerializeHelper.VEC3_STREAM_CODEC.decode(buffer)
                        val uv = SerializeHelper.VEC2_STREAM_CODEC.decode(buffer)
                        vertices.add(OMeshVertex(pos, normal, uv))
                    }
                    polygons.add(OMeshPolygon(vertices))
                }
                
                return OMesh(normalizedUvs, textureWidth, textureHeight, polygons)
            }
            
            override fun encode(buffer: FriendlyByteBuf, mesh: OMesh) {
                buffer.writeBoolean(mesh.normalizedUvs)
                buffer.writeInt(mesh.textureWidth)
                buffer.writeInt(mesh.textureHeight)
                buffer.writeInt(mesh.polygons.size)
                
                for (polygon in mesh.polygons) {
                    buffer.writeByte(polygon.vertices.size)
                    for (vertex in polygon.vertices) {
                        SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, vertex.position)
                        SerializeHelper.VEC3_STREAM_CODEC.encode(buffer, vertex.normal)
                        SerializeHelper.VEC2_STREAM_CODEC.encode(buffer, vertex.uv)
                    }
                }
            }
        }

        /**
         * 用于列表序列化的StreamCodec
         */
        @JvmStatic
        val LIST_STREAM_CODEC: StreamCodec<FriendlyByteBuf, List<OMesh>> = STREAM_CODEC.apply(ByteBufCodecs.collection { mutableListOf() })
            .map({ it.toList() }, { it.toMutableList() })
    }
}