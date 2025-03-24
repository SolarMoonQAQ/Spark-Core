package cn.solarmoon.spark_core.physics

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import java.util.concurrent.atomic.AtomicReference

class PhysicsSync(
    val pco: PhysicsCollisionObject
) {

    private val physicsBuffers = arrayOf(PhysicsState(), PhysicsState())
    private val currentBuffer = AtomicReference(physicsBuffers[0])
    private val lastBuffer get() = if (currentBuffer.get() == physicsBuffers[0]) physicsBuffers[1] else physicsBuffers[0]

    // 复用临时对象减少 GC
    private val tempVec = Vector3f()
    private val tempQuat = Quaternion()

    fun update() {
        val writeBuffer = lastBuffer // 写入到非当前活跃的缓冲区

        pco.getPhysicsLocation(tempVec)
        writeBuffer.position.set(tempVec.toVector3f())

        pco.getPhysicsRotation(tempQuat)
        writeBuffer.rotation.set(tempQuat.toQuaternionf())

        pco.getScale(tempVec)
        writeBuffer.scale.set(tempVec.toVector3f())

        currentBuffer.set(writeBuffer)
    }

    fun getBuffer(partialTicks: Float = 1f) = lastBuffer.lerp(partialTicks, currentBuffer.get())

}