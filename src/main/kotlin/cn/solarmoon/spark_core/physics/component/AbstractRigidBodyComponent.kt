package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.delta_sync.DiffSyncField
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toMatrix3f
import cn.solarmoon.spark_core.util.Subscription
import cn.solarmoon.spark_core.util.onEvent
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toRadians
import cn.solarmoon.spark_core.util.toVec3
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class AbstractRigidBodyComponent<B: PhysicsRigidBody>(
    name: String,
    authority: Authority,
    level: Level,
    diffSyncSchema: DiffSyncSchema<out AbstractRigidBodyComponent<*>>,
    type: CollisionObjectType<out CollisionObjectComponent<*>>,
    body: B
): CollisionObjectComponent<B>(name, authority, level, diffSyncSchema, type, body) {

    @DiffSyncField override var position by setterField(body.getPhysicsLocation(null).toVector3f()) { body.setPhysicsLocation(it.toBVector3f()) }
    @DiffSyncField override var rotation by setterField(body.getPhysicsRotation(null).toQuaternionf()) { body.setPhysicsRotation(it.toBQuaternion()) }
    @DiffSyncField override var scale by setterField(body.getScale(null).toVector3f()) { body.setPhysicsScale(it.toBVector3f()) }
    var isKinematic: Boolean by setterField(body.isKinematic) { body.isKinematic = it }
    @DiffSyncField var isGravityProtected by setterField(body.isGravityProtected) { body.setProtectGravity(it) }
    @DiffSyncField var gravity by setterField(body.getGravity(null).toVector3f()) { body.setGravity(it.toBVector3f()) }
    @DiffSyncField var angularFactor by setterField(body.getAngularFactor(null).toVector3f()) { body.setAngularFactor(it.toBVector3f()) }
    @DiffSyncField var angularVelocity by setterField(body.getAngularVelocity(null).toVector3f()) { body.setAngularVelocity(it.toBVector3f()) }
    @DiffSyncField var angularDamping by setterField(body.angularDamping) { body.angularDamping = it }
    @DiffSyncField var angularSleepingThreshold by setterField(body.angularSleepingThreshold) { body.angularSleepingThreshold = it }
    @DiffSyncField var linearFactor by setterField(body.getLinearFactor(null).toVector3f()) { body.setLinearFactor(it.toBVector3f()) }
    @DiffSyncField var linearVelocity by setterField(body.getLinearVelocity(null).toVector3f()) { body.setLinearVelocity(it.toBVector3f()) }
    @DiffSyncField var linearDamping by setterField(body.linearDamping) { body.linearDamping = it }
    @DiffSyncField var linearSleepingThreshold by setterField(body.linearSleepingThreshold) { body.linearSleepingThreshold = it }
    @DiffSyncField var inverseInertiaLocal by setterField(body.getInverseInertiaLocal(null).toVector3f()) { body.setInverseInertiaLocal(it.toBVector3f()) }
    var inverseInertiaWorld = body.getInverseInertiaWorld(null).toMatrix3f()
    @DiffSyncField var mass by setterField(body.mass) { body.mass = it }

    private var sub: Subscription? = null

    override fun update() {
        super.update()
        isKinematic = body.isKinematic
        isGravityProtected = body.isGravityProtected
        gravity = body.getGravity(null).toVector3f()
        angularFactor = body.getAngularFactor(null).toVector3f()
        angularVelocity = body.getAngularVelocity(null).toVector3f()
        angularDamping = body.angularDamping
        angularSleepingThreshold = body.angularSleepingThreshold
        linearFactor = body.getLinearFactor(null).toVector3f()
        linearVelocity = body.getLinearVelocity(null).toVector3f()
        linearDamping = body.linearDamping
        linearSleepingThreshold = body.linearSleepingThreshold
        inverseInertiaLocal = body.getInverseInertiaLocal(null).toVector3f()
        inverseInertiaWorld = body.getInverseInertiaWorld(null).toMatrix3f()
        mass = body.mass
    }

    /**
     * 绑定到指定骨骼的枢轴点
     *
     * 此处为运动学绑定，会一直紧贴在指定骨骼上
     */
    fun attachToBone(owner: IAnimatable<*>, boneName: String, offset: Vector3f = Vector3f()) {
        if (!authority.isInRightSide(level)) return
        detach()
        sub = onEvent<CollisionObjectEvent.PhysicsTick.Pre> { // 动画在物理线程运行所以频率和其一致
            owner.modelController.model?.pose?.getBonePose(boneName)?.let { pose ->
                val ma = pose.getWorldBonePivotMatrix()
                ma.translate(offset)
                body.setPhysicsLocation(ma.transformPosition(Vector3f()).toBVector3f())
                body.setPhysicsRotation(ma.getUnnormalizedRotation(Quaternionf()).toBQuaternion())
                body.setPhysicsScale(ma.getScale(Vector3f()).toBVector3f())
            }
        }
    }

    /**
     * 绑定到指定骨骼的locator
     *
     * 此处为运动学绑定，会一直紧贴在指定骨骼上
     */
    fun attachToLocator(owner: IAnimatable<*>, locatorName: String, offset: Vector3f = Vector3f()) {
        if (!authority.isInRightSide(level)) return
        detach()
        sub = onEvent<CollisionObjectEvent.PhysicsTick.Pre> {
            owner.modelController.model?.pose?.let { pose ->
                val ma = pose.getWorldBoneLocatorMatrix(locatorName)
                ma.translate(offset)
                body.setPhysicsLocation(ma.transformPosition(Vector3f()).toBVector3f())
                body.setPhysicsRotation(ma.getUnnormalizedRotation(Quaternionf()).toBQuaternion())
                body.setPhysicsScale(ma.getScale(Vector3f()).toBVector3f())
            }
        }
    }

    fun attachToEntity(entity: Entity, offset: Vector3f = Vector3f()) {
        if (!authority.isInRightSide(level)) return
        detach()
        sub = onEvent<CollisionObjectEvent.PhysicsTick.Pre> {
            val ma = Matrix4f().translate(entity.boundingBox.center.add(offset.toVec3()).toVector3f()).rotateY(entity.yRot.toRadians())
            body.setPhysicsLocation(ma.transformPosition(Vector3f()).toBVector3f())
        }
    }

    fun detach() {
        sub?.dispose()
    }

}