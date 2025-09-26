package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toMatrix3f
import cn.solarmoon.spark_core.util.getVec3
import cn.solarmoon.spark_core.util.putVec3
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.util.toDegrees
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class RigidBodyEntity(
    type: EntityType<*>,
    level: Level
): CollisionObjectEntity(type, level) {

    companion object {
        val DATA_GRAVITY = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_IS_GRAVITY_PROTECTED = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DATA_ANGULAR_FACTOR = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_ANGULAR_VELOCITY = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_ANGULAR_DUMPING = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_ANGULAR_SLEEPING_THRESHOLD = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_LINEAR_FACTOR = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_LINEAR_VELOCITY = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_LINEAR_DUMPING = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_LINEAR_SLEEPING_THRESHOLD = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_INVERSE_INERTIA_LOCAL = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_MASS = SynchedEntityData.defineId(RigidBodyEntity::class.java, EntityDataSerializers.FLOAT)
    }

    abstract override val body: PhysicsRigidBody

    override fun setPos(x: Double, y: Double, z: Double) {
        super.setPos(x, y, z)
        if (!updating) {
            physicsLevel.submitImmediateTask {
                body.setPhysicsLocation(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).toBVector3f())
            }
        }
    }

    override fun setXRot(xRot: Float) {
        super.xRot = xRot
        if (!updating) {
            physicsLevel.submitImmediateTask {
                val origin = body.getPhysicsRotation(null).toQuaternionf().toEuler().toDegrees()
                body.setPhysicsRotation(Quaternionf().rotateXYZ(xRot, origin.y, origin.z).toBQuaternion())
            }
        }
    }

    override fun setYRot(yRot: Float) {
        super.yRot = yRot
        if (!updating) {
            physicsLevel.submitImmediateTask {
                val origin = body.getPhysicsRotation(null).toQuaternionf().toEuler().toDegrees()
                body.setPhysicsRotation(Quaternionf().rotateXYZ(origin.x, yRot, origin.z).toBQuaternion())
            }
        }
    }

    override var zRot by syncField(DATA_ZROT, { it }, { it }, {
        val origin = body.getPhysicsRotation(null).toQuaternionf().toEuler().toDegrees()
        body.setPhysicsRotation(Quaternionf().rotateXYZ(origin.x, origin.y, it).toBQuaternion())
                                                              }, needAutoUpdate = true)

    override var rotation: Vec3
        get() = Vector3f(xRot, yRot, zRot).toVec3()
        set(value) {
            setRot(value.y.toFloat(), value.x.toFloat())
            zRot = value.z.toFloat() % 360.0f
        }

    override var scale by syncField(DATA_SCALE, { it.toVec3() }, { it.toVector3f() }, { body.setPhysicsScale(it.toBVector3f()) }, needAutoUpdate = true)

    var isKinematic
        get() = body.isKinematic
        set(value) {
            physicsLevel.submitImmediateTask {
                body.isKinematic = value
            }
        }

    var gravity by syncField(DATA_GRAVITY, { it.toVec3() }, { it.toVector3f() }, { body.setGravity(it.toBVector3f()) })
    var isGravityProtected by syncField(DATA_IS_GRAVITY_PROTECTED, { it }, { it }, { body.setProtectGravity(it) })
    var angularFactor by syncField(DATA_ANGULAR_FACTOR, { it.toVec3() }, { it.toVector3f() }, { body.setAngularFactor(it.toBVector3f()) })
    var angularVelocity by syncField(DATA_ANGULAR_VELOCITY, { it.toVec3() }, { it.toVector3f() }, { body.setAngularVelocity(it.toBVector3f()) })
    var angularDamping by syncField(DATA_ANGULAR_DUMPING, { it }, { it }, { body.angularDamping = it })
    var angularSleepingThreshold by syncField(DATA_ANGULAR_SLEEPING_THRESHOLD, { it }, { it }, { body.angularSleepingThreshold = it })
    var linearFactor by syncField(DATA_LINEAR_FACTOR, { it.toVec3() }, { it.toVector3f() }, { body.setLinearFactor(it.toBVector3f()) })
    var linearVelocity by syncField(DATA_LINEAR_VELOCITY, { it.toVec3() }, { it.toVector3f() }, { body.setLinearVelocity(it.toBVector3f()) })
    var linearDamping by syncField(DATA_LINEAR_DUMPING, { it }, { it }, { body.linearDamping = it })
    var linearSleepingThreshold by syncField(DATA_LINEAR_SLEEPING_THRESHOLD, { it }, { it }, { body.linearSleepingThreshold = it })
    var inverseInertiaLocal by syncField(DATA_INVERSE_INERTIA_LOCAL, { it.toVec3() }, { it.toVector3f() }, { body.setInverseInertiaLocal(it.toBVector3f()) })
    val inverseInertiaWorld get() = body.getInverseInertiaWorld(null).toMatrix3f()
    var mass by syncField(DATA_MASS, { it }, { it }, { body.setMass(it) })

    override fun recreateFromPacket(packet: ClientboundAddEntityPacket) {
        super.recreateFromPacket(packet)
        isKinematic = false // 客户端默认为运动学（运动由服务端权威下发）
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_GRAVITY, body.getGravity(null).toVector3f())
        builder.define(DATA_IS_GRAVITY_PROTECTED, body.isGravityProtected)
        builder.define(DATA_ANGULAR_FACTOR, body.getAngularFactor(null).toVector3f())
        builder.define(DATA_ANGULAR_VELOCITY, body.getAngularVelocity(null).toVector3f())
        builder.define(DATA_ANGULAR_DUMPING, body.angularDamping)
        builder.define(DATA_ANGULAR_SLEEPING_THRESHOLD, body.angularSleepingThreshold)
        builder.define(DATA_LINEAR_FACTOR, body.getLinearFactor(null).toVector3f())
        builder.define(DATA_LINEAR_VELOCITY, body.getLinearVelocity(null).toVector3f())
        builder.define(DATA_LINEAR_DUMPING, body.linearDamping)
        builder.define(DATA_LINEAR_SLEEPING_THRESHOLD, body.linearSleepingThreshold)
        builder.define(DATA_INVERSE_INERTIA_LOCAL, body.getInverseInertiaLocal(null).toVector3f())
        builder.define(DATA_MASS, body.mass)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        isKinematic = compound.getBoolean("physics_isKinematic")
        gravity = compound.getVec3("physics_gravity")
        isGravityProtected = compound.getBoolean("physics_isGravityProtected")
        angularFactor = compound.getVec3("physics_angularFactor")
        angularVelocity = compound.getVec3("physics_angularVelocity")
        angularDamping = compound.getFloat("physics_angularDamping")
        angularSleepingThreshold = compound.getFloat("physics_angularSleepingThreshold")
        linearFactor = compound.getVec3("physics_linearFactor")
        linearVelocity = compound.getVec3("physics_linearVelocity")
        linearDamping = compound.getFloat("physics_linearDamping")
        linearSleepingThreshold = compound.getFloat("physics_linearSleepingThreshold")
        inverseInertiaLocal = compound.getVec3("physics_inverseInertiaLocal")
        mass = compound.getFloat("physics_mass")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean("physics_isKinematic", isKinematic)
        compound.putVec3("physics_gravity", gravity)
        compound.putBoolean("physics_isGravityProtected", isGravityProtected)
        compound.putVec3("physics_angularFactor", angularFactor)
        compound.putVec3("physics_angularVelocity", angularVelocity)
        compound.putFloat("physics_angularDamping", angularDamping)
        compound.putFloat("physics_angularSleepingThreshold", angularSleepingThreshold)
        compound.putVec3("physics_linearFactor", linearFactor)
        compound.putVec3("physics_linearVelocity", linearVelocity)
        compound.putFloat("physics_linearDamping", linearDamping)
        compound.putFloat("physics_linearSleepingThreshold", linearSleepingThreshold)
        compound.putVec3("physics_inverseInertiaLocal", inverseInertiaLocal)
        compound.putFloat("physics_mass", mass)
    }

}