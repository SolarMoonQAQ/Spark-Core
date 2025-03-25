package cn.solarmoon.spark_core.physics

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

class PhysicsSync(
    private val pco: PhysicsCollisionObject
) {

    private val frontBuffer = AtomicReference(PhysicsState())
    private val backBuffer = AtomicReference(PhysicsState())

    private var lastBuffer = AtomicReference(PhysicsState())

    private val tempVec = Vector3f()
    private val tempQuat = Quaternion()

    // 物理线程调用：高频更新后台缓冲（60tick）
    fun update() {
        backBuffer.updateAndGet { currentBack ->
            pco.getPhysicsLocation(tempVec)
            val pos = tempVec.toVector3f()
            pco.getPhysicsRotation(tempQuat)
            val rot = tempQuat.toQuaternionf()
            pco.getScale(tempVec)
            val scale = tempVec.toVector3f()
            PhysicsState(pos, rot, scale)
        }
    }

    // 主线程调用：在 Tick 开始时原子交换缓冲（20tick）
    fun swapBuffers() {
        lastBuffer = AtomicReference(frontBuffer.get())
        frontBuffer.set(backBuffer.get())
    }

    // 主线程或渲染线程调用：获取插值后的缓冲
    fun getBuffer(partialTicks: Float = 1f): PhysicsState {
        return lastBuffer.get().lerp(partialTicks, frontBuffer.get())
    }

}