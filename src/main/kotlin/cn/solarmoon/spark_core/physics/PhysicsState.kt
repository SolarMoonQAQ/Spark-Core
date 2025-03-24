package cn.solarmoon.spark_core.physics

import com.jme3.math.Transform
import org.joml.Quaternionf
import org.joml.Vector3f

data class PhysicsState(
    val position: Vector3f = Vector3f(),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1f)
) {

    val transform get() = Transform(position.toBVector3f(), rotation.toBQuaternion(), scale.toBVector3f())

    fun lerp(delta: Float, target: PhysicsState) = PhysicsState(
        position.lerp(target.position, delta, Vector3f()),
        rotation.slerp(target.rotation, delta, Quaternionf()),
        scale.lerp(target.scale, delta, Vector3f())
    )

}
