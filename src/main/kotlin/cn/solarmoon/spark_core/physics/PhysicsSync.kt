package cn.solarmoon.spark_core.physics

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

class PhysicsSync(
    val pco: PhysicsCollisionObject
) {

    private val physicsBuffers = arrayOf(PhysicsState(), PhysicsState())
    private val currentBuffer = AtomicReference(physicsBuffers[0])
    private var lastBuffer = AtomicReference(physicsBuffers[1])

    private val tempVec = Vector3f()
    private val tempQuat = Quaternion()

    fun update() {
        val writeBuffer = lastBuffer.get() // 写入非当前活跃缓冲区

        pco.getPhysicsLocation(tempVec)
        writeBuffer.position.set(tempVec.toVector3f())

        pco.getPhysicsRotation(tempQuat)
        writeBuffer.rotation.set(tempQuat.toQuaternionf())

        pco.getScale(tempVec)
        writeBuffer.scale.set(tempVec.toVector3f())

        // 交换缓冲区
        val temp = currentBuffer.get()
        currentBuffer.set(writeBuffer)
        lastBuffer.set(temp)
    }

    fun getBuffer(partialTicks: Float = 1f) = lastBuffer.get().lerp(partialTicks, currentBuffer.get())
}