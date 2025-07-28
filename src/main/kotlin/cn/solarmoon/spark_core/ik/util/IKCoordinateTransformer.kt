package cn.solarmoon.spark_core.ik.util

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.math.Vector3f
import org.joml.Matrix4f
import org.joml.Vector3f as JomlVector3f

/**
 * 提供IK系统中世界坐标和本地坐标之间转换的工具类。
 *
 * 核心原则是：动画先计算基础姿势，IK后调整基础姿势。
 * 所有IK计算应该在本地坐标空间中进行，以确保与动画系统的正确集成。
 */
object IKCoordinateTransformer {

    /**
     * 将JME Vector3f格式的世界坐标转换为模型的本地坐标空间。
     *
     * @param animatable 动画体实例
     * @param worldPosition JME Vector3f格式的世界坐标
     * @return JME Vector3f格式的本地坐标
     */
    fun worldToLocalSpaceJME(animatable: IAnimatable<*>, worldPosition: Vector3f): Vector3f {
        // 获取动画体的世界变换矩阵的逆矩阵
        val worldMatrix = animatable.getWorldPositionMatrix()
        val inverseWorldMatrix = Matrix4f(worldMatrix).invert()

        // 将世界坐标转换为本地坐标
        val worldPosVector = worldPosition.toVector3f()

        val localPosVector = JomlVector3f()
        inverseWorldMatrix.transformPosition(worldPosVector, localPosVector)

        return Vector3f(
            -localPosVector.x,
            -localPosVector.y,
            -localPosVector.z
        )
    }

    /**
     * 将JME Vector3f格式的本地坐标转换为世界坐标。
     *
     * @param animatable 动画体实例
     * @param localPosition JME Vector3f格式的本地坐标
     * @return JME Vector3f格式的世界坐标
     */
    fun localToWorldSpaceJME(animatable: IAnimatable<*>, localPosition: Vector3f): Vector3f {
        // 获取动画体的世界变换矩阵
        val worldMatrix = animatable.getWorldPositionMatrix()

        // 将本地坐标转换为世界坐标
        val localPosVector = JomlVector3f(
            localPosition.x,
            localPosition.y,
            localPosition.z
        )

        val worldPosVector = JomlVector3f()
        worldMatrix.transformPosition(localPosVector, worldPosVector)

        return Vector3f(
            worldPosVector.x,
            worldPosVector.y,
            worldPosVector.z
        )
    }
}
