package cn.solarmoon.spark_core.physics.mesh

import cn.solarmoon.spark_core.util.copy
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 *       7-------6
 *      /|      /|
 *     3-+-----2 |
 *     | 4-----+-5
 *     |/      |/
 *     0-------1
 */
class BoxShapeMesh {

    lateinit var halfSize: Vector3f
        private set
    lateinit var size: Vector3f
        private set
    lateinit var vertices: Array<Vector3f>
        private set

    val edgesOrder = intArrayOf(
        0,1, 1,2, 2,3, 3,0, // 前面
        4,5, 5,6, 6,7, 7,4, // 后面
        0,4, 1,5, 2,6, 3,7  // 连接前后面的边
    )

    fun update(box: BoxCollisionShape) = apply {
        halfSize = box.getHalfExtents(null).toVector3f()

        size = halfSize.mul(2f, Vector3f())

        vertices = arrayOf(
            Vector3f(-halfSize.x, -halfSize.y, -halfSize.z), // 0
            Vector3f(+halfSize.x, -halfSize.y, -halfSize.z), // 1
            Vector3f(+halfSize.x, +halfSize.y, -halfSize.z), // 2
            Vector3f(-halfSize.x, +halfSize.y, -halfSize.z), // 3
            Vector3f(-halfSize.x, -halfSize.y, +halfSize.z), // 4
            Vector3f(+halfSize.x, -halfSize.y, +halfSize.z), // 5
            Vector3f(+halfSize.x, +halfSize.y, +halfSize.z), // 6
            Vector3f(-halfSize.x, +halfSize.y, +halfSize.z)  // 7
        )
    }

    fun getWorldVertexPosition(order: Int, transform: Matrix4f): Vector3f {
        return transform.transformPosition(vertices[order].copy())
    }

}