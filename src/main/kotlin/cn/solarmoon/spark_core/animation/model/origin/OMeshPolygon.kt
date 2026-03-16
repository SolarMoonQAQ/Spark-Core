package cn.solarmoon.spark_core.animation.model.origin

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * 网格多边形面，由多个顶点组成
 * 支持三角形、四边形等任意凸多边形
 * 
 * @property vertices 多边形顶点列表
 * @property vertexCount 顶点数量（缓存）
 */
data class OMeshPolygon(
    val vertices: List<OMeshVertex>
) {
    /**
     * 顶点数量（缓存）
     */
    val vertexCount: Int = vertices.size
    
    /**
     * 计算多边形中心（用于面剔除）
     */
    val center: Vec3 by lazy {
        vertices.fold(Vec3.ZERO) { acc, v -> acc.add(v.position) }
            .scale(1.0 / vertices.size)
    }
    
    /**
     * 计算面法线（备用，当顶点法线失效时使用）
     * 使用前三个顶点计算多边形面的法线
     */
    val faceNormal: Vec3 by lazy {
        if (vertices.size >= 3) {
            val v0 = vertices[0].position
            val v1 = vertices[1].position
            val v2 = vertices[2].position
            v1.subtract(v0).cross(v2.subtract(v0)).normalize()
        } else {
            // 默认向上法线
            Vec3(0.0, 1.0, 0.0)
        }
    }
    
    /**
     * 检查多边形是否为三角形
     */
    val isTriangle: Boolean get() = vertexCount == 3
    
    /**
     * 检查多边形是否为四边形
     */
    val isQuad: Boolean get() = vertexCount == 4
    
    companion object {
        /**
         * 从原始索引数据创建多边形
         * 
         * @param polyIndices 多边形索引数组 [[位置索引, 法线索引, UV索引], ...]
         * @param positions 位置数据数组
         * @param normals 法线数据数组
         * @param uvs UV数据数组
         * @param textureWidth 纹理宽度
         * @param textureHeight 纹理高度
         * @param normalizedUvs UV是否已归一化
         * @return 构建好的多边形
         */
        fun fromRawIndices(
            polyIndices: List<List<Int>>,
            positions: List<List<Float>>,
            normals: List<List<Float>>,
            uvs: List<List<Float>>,
            textureWidth: Int,
            textureHeight: Int,
            normalizedUvs: Boolean
        ): OMeshPolygon {
            val vertices = polyIndices.map { indices ->
                val (posIdx, normalIdx, uvIdx) = indices
                
                OMeshVertex.fromRawData(
                    rawPos = Vec3(
                        positions[posIdx][0].toDouble(),
                        positions[posIdx][1].toDouble(),
                        positions[posIdx][2].toDouble()
                    ),
                    rawNormal = Vec3(
                        normals[normalIdx][0].toDouble(),
                        normals[normalIdx][1].toDouble(),
                        normals[normalIdx][2].toDouble()
                    ),
                    rawUv = Vec2(
                        uvs[uvIdx][0],
                        uvs[uvIdx][1]
                    ),
                    textureWidth = textureWidth,
                    textureHeight = textureHeight,
                    normalizedUvs = normalizedUvs
                )
            }
            
            return OMeshPolygon(vertices)
        }
    }
}