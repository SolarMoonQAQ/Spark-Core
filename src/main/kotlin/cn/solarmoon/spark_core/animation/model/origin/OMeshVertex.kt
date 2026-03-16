package cn.solarmoon.spark_core.animation.model.origin

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * 网格顶点数据，包含位置、法线、UV坐标
 * 与现有的OVertex隔离，专用于Mesh渲染
 * 
 * @property position 顶点位置（模型空间局部坐标）
 * @property normal 顶点法线（归一化）
 * @property uv 纹理坐标（已归一化到0-1范围）
 */
data class OMeshVertex(
    val position: Vec3,
    val normal: Vec3,
    val uv: Vec2
) {
    /**
     * 转换为渲染用的浮点数数组
     * 格式：[x, y, z, nx, ny, nz, u, v]
     */
    fun toFloatArray(): FloatArray = floatArrayOf(
        position.x.toFloat(), position.y.toFloat(), position.z.toFloat(),
        normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat(),
        uv.x, uv.y
    )
    
    companion object {
        /**
         * 从原始数据创建顶点，应用与OCube相同的坐标转换规则
         * 
         * @param rawPos 原始位置坐标（基岩版模型空间）
         * @param rawNormal 原始法线向量
         * @param rawUv 原始UV坐标
         * @param textureWidth 纹理宽度（用于UV归一化）
         * @param textureHeight 纹理高度（用于UV归一化）
         * @param normalizedUvs 是否已归一化（true则跳过UV缩放）
         * @return 转换后的顶点数据
         */
        fun fromRawData(
            rawPos: Vec3,
            rawNormal: Vec3,
            rawUv: Vec2,
            textureWidth: Int,
            textureHeight: Int,
            normalizedUvs: Boolean
        ): OMeshVertex {
            // 应用与OCube相同的坐标转换：X轴取反，所有坐标除以16
            val position = Vec3(
                -rawPos.x / 16.0,  // X轴取反
                rawPos.y / 16.0,    // Y轴不变
                rawPos.z / 16.0     // Z轴不变
            )

            val normal = Vec3(
                - rawNormal.x,
                rawNormal.y,
                rawNormal.z
            ).normalize()
            
            // UV处理：根据normalizedUvs标志决定是否归一化
            val uv = if (normalizedUvs) {
                rawUv
            } else {
                Vec2(
                    (rawUv.x / textureWidth.toDouble()).toFloat(),
                    (rawUv.y / textureHeight.toDouble()).toFloat()
                )
            }
            
            return OMeshVertex(position, normal, uv)
        }
    }
}