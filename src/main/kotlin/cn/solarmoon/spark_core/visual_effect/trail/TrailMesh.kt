package cn.solarmoon.spark_core.visual_effect.trail

import org.joml.Vector2f
import org.joml.Vector3f

class TrailMesh(
    val info: TrailInfo
) {

    val mainPoints = mutableListOf<TrailPoint>()
    val renderPoints = mutableListOf<TrailPoint>()

    var dynamicStart: ((Float) -> Vector3f)? = null
    var dynamicEnd: ((Float) -> Vector3f)? = null

    fun addMainPoint(point: TrailPoint) {
        mainPoints.add(point)
    }

    fun addRenderPoint(point: TrailPoint) {
        renderPoints.add(point)
    }

    fun tick() {
        mainPoints.forEach { it.tick() }
        if (mainPoints.all { it.isExpired }) {
            dynamicStart = null
            dynamicEnd = null
            mainPoints.clear()
        }
        renderPoints.forEach { it.tick() }
        if (renderPoints.all { it.isExpired }) { renderPoints.clear() }
    }

    fun frame(partialTicks: Float) {
        generateRenderPoint(partialTicks)?.let {
            addRenderPoint(it)
        }
    }

    /**
     * 将渲染点两两配对并计算UV坐标
     */
    fun zipRenderPoints(): List<TrailElement> {
        if (renderPoints.size < 2) return emptyList()

        // 1. 计算轨迹总长度
        val totalLength = calculateTotalLength(renderPoints)

        // 2. 重新计算UV（基于累计长度比例）
        val elements = mutableListOf<TrailElement>()
        var accumulatedLength = 0f

        for (i in 0 until renderPoints.size - 1) {
            val pointA = renderPoints[i]
            val pointB = renderPoints[i + 1]
            val segmentLength = pointA.center.distance(pointB.center)

            // 计算当前线段起止UV
            val uStart = accumulatedLength / totalLength
            val uEnd = (accumulatedLength + segmentLength) / totalLength

            // 设置四边形四个顶点的UV
            val uv1 = Vector2f(uStart, 0f) // A点左侧
            val uv2 = Vector2f(uStart, 1f) // A点右侧
            val uv3 = Vector2f(uEnd, 0f)   // B点左侧
            val uv4 = Vector2f(uEnd, 1f)   // B点右侧

            elements.add(TrailElement(
                pointA, pointB,
                uv1, uv2, uv3, uv4
            ))

            accumulatedLength += segmentLength
        }

        return elements
    }

    fun generateRenderPoint(partialTicks: Float): TrailPoint? {
        if (dynamicStart == null || dynamicEnd == null) return null
        return TrailPoint(dynamicStart!!(partialTicks), dynamicEnd!!(partialTicks), info)
    }

    /**
     * 计算轨迹总长度
     */
    private fun calculateTotalLength(points: List<TrailPoint>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += points[i].center.distance(points[i-1].center)
        }
        return length
    }

}

data class TrailElement(
    val p1: TrailPoint,
    val p2: TrailPoint,
    val uv1: Vector2f,
    val uv2: Vector2f,
    val uv3: Vector2f,
    val uv4: Vector2f
)