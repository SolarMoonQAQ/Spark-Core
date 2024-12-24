package cn.solarmoon.spark_core.animation.model.part

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

data class VertexSet(
    val bottomLeftBack: Vertex,
    val bottomRightBack: Vertex,
    val topLeftBack: Vertex,
    val topRightBack: Vertex,
    val topLeftFront: Vertex,
    val topRightFront: Vertex,
    val bottomLeftFront: Vertex,
    val bottomRightFront: Vertex
) {
    constructor(origin: Vec3, size: Vec3, inflate: Double) : this(
        Vertex((origin.x - inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z - inflate).toFloat()),
        Vertex((origin.x - inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        Vertex((origin.x - inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z - inflate).toFloat()),
        Vertex((origin.x - inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        Vertex((origin.x + size.x + inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z - inflate).toFloat()),
        Vertex((origin.x + size.x + inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        Vertex((origin.x + size.x + inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z - inflate).toFloat()),
        Vertex((origin.x + size.x + inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z + size.z + inflate).toFloat())
    )

    val quadWest get() = arrayOf(this.topRightBack, this.topLeftBack, this.bottomLeftBack, this.bottomRightBack)

    val quadEast get() = arrayOf(this.topLeftFront, this.topRightFront, this.bottomRightFront, this.bottomLeftFront)

    val quadNorth get() = arrayOf(this.topLeftBack, this.topLeftFront, this.bottomLeftFront, this.bottomLeftBack)

    val quadSouth get() = arrayOf(this.topRightFront, this.topRightBack, this.bottomRightBack, this.bottomRightFront)

    val quadUp get() = arrayOf(this.topRightBack, this.topRightFront, this.topLeftFront, this.topLeftBack)

    val quadDown get() = arrayOf(this.bottomLeftBack, this.bottomLeftFront, this.bottomRightFront, this.bottomRightBack)

    fun verticesForQuad(direction: Direction, boxUV: Boolean, mirror: Boolean): Array<Vertex> {
        return when (direction) {
            Direction.WEST -> if (mirror) quadEast else quadWest
            Direction.EAST -> if (mirror) quadWest else quadEast
            Direction.NORTH -> quadNorth
            Direction.SOUTH -> quadSouth
            Direction.UP -> if (mirror && !boxUV) quadDown else quadUp
            Direction.DOWN -> if (mirror && !boxUV) quadUp else quadDown
        }
    }

}
