package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3d
import cn.solarmoon.spark_core.util.*
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Quaternionf
import org.joml.Vector3f


private val states = mutableMapOf<PhysicsCollisionObject, PhysicsBodyState>()
fun stateOf(collisionObject: PhysicsCollisionObject): PhysicsBodyState {
    return states.getOrPut(collisionObject) { PhysicsBodyState(collisionObject) }
}

var PhysicsCollisionObject.owner: PhysicsHost?
    get() = userObject as? PhysicsHost
    set(value) {
        val event = triggerEvent(PhysicsBodyEvent.OwnerChanged(owner, value))
        val value = event.newOwner
        value?.let {
            it.allPhysicsBodies[name] = this
        } ?: owner?.allPhysicsBodies?.remove(name)
        userObject = value
    }

fun Level.addPhysicsBody(body: PhysicsCollisionObject) {
    physicsLevel.apply {
        submitImmediateTask {
            world.addCollisionObject(body)
        }
    }
}

fun Level.removePhysicsBody(body: PhysicsCollisionObject) {
    physicsLevel.apply {
        submitImmediateTask {
            body.owner = null
            if (body.isInWorld)
                world.removeCollisionObject(body)
        }
    }
}

private val subs = mutableMapOf<PhysicsCollisionObject, Subscription>()

fun PhysicsCollisionObject.onCollideStarted(handler: (PhysicsBodyEvent.Collide.Started) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Started> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollideProcessed(handler: (PhysicsBodyEvent.Collide.Processed) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Processed> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollideEnded(handler: (PhysicsBodyEvent.Collide.Ended) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Ended> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollidePre(handler: (PhysicsBodyEvent.Collide.Pre) -> Boolean): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Pre> { event ->
        // 调用处理器，得到返回值（true=继续，false=取消）
        val shouldCancel = !handler(event)   // 返回 true 表示允许碰撞，false 表示取消
        if (shouldCancel) {
            event.canceled = true
        }
    }
}

fun PhysicsCollisionObject.onTick(handler: (PhysicsBodyEvent.Tick) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Tick> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onPrePhysicsTick(handler: (PhysicsBodyEvent.PhysicsTick.Pre) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.PhysicsTick.Pre> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onPostPhysicsTick(handler: (PhysicsBodyEvent.PhysicsTick.Post) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.PhysicsTick.Post> { event ->
        handler(event)
    }
}

/**
 * 绑定到指定骨骼的枢轴点
 *
 * 此处为运动学绑定，会一直紧贴在指定骨骼上
 */
fun PhysicsRigidBody.attachToBone(owner: IAnimatable<*>, boneName: String, offset: Vector3f = Vector3f()) {
    detach()
    subs[this] = onEvent<PhysicsBodyEvent.Tick> { // 动画在物理线程运行所以频率和其一致
        owner.modelController.model?.pose?.getBonePose(boneName)?.let { pose ->
            val ma = pose.getWorldBonePivotMatrix()
            ma.translate(offset)
            // 这里刻意保持 float：源矩阵是 joml Matrix4f，绝对平移量在取出时就已经是 float 精度，
            // 改用 Dp 写入不会增加任何精度，只会多一次 Vec3d 分配（见文档 §4.4）
            setPhysicsLocation(ma.transformPosition(Vector3f()).toBVector3f())
            setPhysicsRotation(ma.getUnnormalizedRotation(Quaternionf()).toBQuaternion())
            setPhysicsScale(ma.getScale(Vector3f()).toBVector3f())
        }
    }
}

/**
 * 绑定到指定骨骼的locator
 *
 * 此处为运动学绑定，会一直紧贴在指定骨骼上
 */
fun PhysicsRigidBody.attachToLocator(owner: IAnimatable<*>, locatorName: String, offset: Vector3f = Vector3f()) {
    detach()
    subs[this] = onEvent<PhysicsBodyEvent.PhysicsTick.Pre> {
        owner.modelController.model?.pose?.let { pose ->
            val ma = pose.getWorldBoneLocatorMatrix(locatorName)
            ma.translate(offset)
            // 同 attachToBone：源矩阵是 Matrix4f，改 Dp 无精度收益
            setPhysicsLocation(ma.transformPosition(Vector3f()).toBVector3f())
            setPhysicsRotation(ma.getUnnormalizedRotation(Quaternionf()).toBQuaternion())
            setPhysicsScale(ma.getScale(Vector3f()).toBVector3f())
        }
    }
}

fun PhysicsRigidBody.attachToEntity(entity: Entity, offset: Vector3f = Vector3f()) {
    detach()
    subs[this] = onEvent<PhysicsBodyEvent.Tick> {
        // 实体的绝对位置本来就是 double（MC Vec3），这里走 Dp 写入，避免中途被窄化成 float（决策 D4）
        setPhysicsLocationDp(entity.boundingBox.center.add(offset.toVec3()).toVec3d())
    }
}

fun PhysicsRigidBody.detach() {
    subs[this]?.dispose()
}