package cn.solarmoon.spark_core.api.animation.model.helper

import cn.solarmoon.spark_core.api.animation.model.part.CubePart
import net.minecraft.core.Direction

/**
 * 多边形的面，能够修正传入顶点的正确映射位置，并给出法线方向
 */
data class Polygon(//
    val vertexes: Array<Vertex>,
    var u1: Float, var v1: Float,
    var u2: Float, var v2: Float,
    val textureWith: Float, val textureHeight: Float,
    val mirror: Boolean,
    val direction: Direction
) {
    val normal = direction.step()

    init {
        if (!mirror) {
            val tempWidth = u2
            u2 = u1
            u1 = tempWidth
        }
        else {
            normal.mul(-1f, 1f, 1f)
        }

        vertexes[0] = vertexes[0].remap(u1 / textureWith, v1 / textureHeight)
        vertexes[1] = vertexes[1].remap(u2 / textureWith, v1 / textureHeight)
        vertexes[2] = vertexes[2].remap(u2 / textureWith, v2 / textureHeight)
        vertexes[3] = vertexes[3].remap(u1 / textureWith, v2 / textureHeight)
    }

    companion object {
        fun of(vertexSet: VertexSet, cube: CubePart, direction: Direction): Polygon {
            var u1 = 0f; var v1 = 0f; var u2 = 0f; var v2 = 0f
            val uv = cube.uv
            val size = cube.size
            val uvSet = arrayOf(
                uv.x,
                uv.x + size.z.toFloat(),
                uv.x + size.z.toFloat() + size.x.toFloat(),
                uv.x + size.z.toFloat() + size.x.toFloat() + size.x.toFloat(),
                uv.x + size.z.toFloat() + size.x.toFloat() + size.z.toFloat(),
                uv.x + size.z.toFloat() + size.x.toFloat() + size.z.toFloat() + size.x.toFloat(),
                uv.y,
                uv.y + size.z.toFloat(),
                uv.y + size.z.toFloat() + size.y.toFloat()
            )
            when (direction) {
                Direction.WEST -> {
                    u1 = uvSet[2]; v1 = uvSet[7]
                    u2 = uvSet[4]; v2 = uvSet[8]
                }
                Direction.EAST -> {
                    u1 = uvSet[0]; v1 = uvSet[7]
                    u2 = uvSet[1]; v2 = uvSet[8]
                }
                Direction.NORTH -> {
                    u1 = uvSet[1]; v1 = uvSet[7]
                    u2 = uvSet[2]; v2 = uvSet[8]
                }
                Direction.SOUTH -> {
                    u1 = uvSet[4]; v1 = uvSet[7]
                    u2 = uvSet[5]; v2 = uvSet[8]
                }
                Direction.UP -> {
                    u1 = uvSet[1]; v1 = uvSet[6]
                    u2 = uvSet[2]; v2 = uvSet[7]
                }
                Direction.DOWN -> {
                    u1 = uvSet[2]; v1 = uvSet[7]
                    u2 = uvSet[3]; v2 = uvSet[7] - size.z.toFloat()
                }
            }
            return Polygon(vertexSet.verticesForQuad(direction, false, cube.mirror), u1, v1, u2, v2, cube.textureWidth.toFloat(), cube.textureHeight.toFloat(), cube.mirror, direction)
        }
    }

}