package cn.solarmoon.spark_core.animation.model.origin

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

data class OVertexSet(
    val bottomLeftBack: OVertex,
    val bottomRightBack: OVertex,
    val topLeftBack: OVertex,
    val topRightBack: OVertex,
    val topLeftFront: OVertex,
    val topRightFront: OVertex,
    val bottomLeftFront: OVertex,
    val bottomRightFront: OVertex
) {
    constructor(origin: Vec3, size: Vec3, inflate: Double) : this(
        OVertex((origin.x - inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z - inflate).toFloat()),
        OVertex((origin.x - inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        OVertex((origin.x - inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z - inflate).toFloat()),
        OVertex((origin.x - inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        OVertex((origin.x + size.x + inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z - inflate).toFloat()),
        OVertex((origin.x + size.x + inflate).toFloat(), (origin.y + size.y + inflate).toFloat(), (origin.z + size.z + inflate).toFloat()),
        OVertex((origin.x + size.x + inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z - inflate).toFloat()),
        OVertex((origin.x + size.x + inflate).toFloat(), (origin.y - inflate).toFloat(), (origin.z + size.z + inflate).toFloat())
    )

    val quadWest get() = arrayOf(this.topRightBack, this.topLeftBack, this.bottomLeftBack, this.bottomRightBack)

    val quadEast get() = arrayOf(this.topLeftFront, this.topRightFront, this.bottomRightFront, this.bottomLeftFront)

    val quadNorth get() = arrayOf(this.topLeftBack, this.topLeftFront, this.bottomLeftFront, this.bottomLeftBack)

    val quadSouth get() = arrayOf(this.topRightFront, this.topRightBack, this.bottomRightBack, this.bottomRightFront)

    val quadUp get() = arrayOf(this.topRightBack, this.topRightFront, this.topLeftFront, this.topLeftBack)

    val quadDown get() = arrayOf(this.bottomLeftBack, this.bottomLeftFront, this.bottomRightFront, this.bottomRightBack)

    fun verticesForQuad(direction: Direction, boxUV: Boolean, mirror: Boolean): Array<OVertex> {
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
