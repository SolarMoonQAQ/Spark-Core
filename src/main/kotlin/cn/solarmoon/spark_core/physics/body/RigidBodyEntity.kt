package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toMatrix3f
import cn.solarmoon.spark_core.util.getVec3
import cn.solarmoon.spark_core.util.ifContains
import cn.solarmoon.spark_core.util.putVec3
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.util.toDegrees
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toRadians
import cn.solarmoon.spark_core.util.toVec3
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

    override fun setPosRaw(x: Double, y: Double, z: Double) {
        super.setPosRaw(x, y, z)
        if (!updating) {
            physicsLevel.submitImmediateTask {
                body.setPhysicsLocation(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).toBVector3f())
            }
        }
    }

    var isKinematic
        get() = body.isKinematic
        set(value) {
            physicsLevel.submitImmediateTask {
                body.isKinematic = value
            }
        }

    override var rotation by syncField(DATA_ROTATION) { body.setPhysicsRotation(it.toBQuaternion()) }
    override var scale by syncField(DATA_SCALE, { it.toVec3() }, { it.toVector3f() }, { body.setPhysicsScale(it.toBVector3f()) })
    var gravity by syncField(DATA_GRAVITY, { it.toVec3() }, { it.toVector3f() }, { body.setGravity(it.toBVector3f()) })
    var isGravityProtected by syncField(DATA_IS_GRAVITY_PROTECTED) { body.setProtectGravity(it) }
    var angularFactor by syncField(DATA_ANGULAR_FACTOR, { it.toVec3() }, { it.toVector3f() }, { body.setAngularFactor(it.toBVector3f()) })
    var angularVelocity by syncField(DATA_ANGULAR_VELOCITY, { it.toVec3() }, { it.toVector3f() }, { body.setAngularVelocity(it.toBVector3f()) })
    var angularDamping by syncField(DATA_ANGULAR_DUMPING) { body.angularDamping = it }
    var angularSleepingThreshold by syncField(DATA_ANGULAR_SLEEPING_THRESHOLD) { body.angularSleepingThreshold = it }
    var linearFactor by syncField(DATA_LINEAR_FACTOR, { it.toVec3() }, { it.toVector3f() }, { body.setLinearFactor(it.toBVector3f()) })
    var linearVelocity by syncField(DATA_LINEAR_VELOCITY, { it.toVec3() }, { it.toVector3f() }, { body.setLinearVelocity(it.toBVector3f()) })
    var linearDamping by syncField(DATA_LINEAR_DUMPING) { body.linearDamping = it }
    var linearSleepingThreshold by syncField(DATA_LINEAR_SLEEPING_THRESHOLD) { body.linearSleepingThreshold = it }
    var inverseInertiaLocal by syncField(DATA_INVERSE_INERTIA_LOCAL, { it.toVec3() }, { it.toVector3f() }, { body.setInverseInertiaLocal(it.toBVector3f()) })
    val inverseInertiaWorld get() = body.getInverseInertiaWorld(null).toMatrix3f()
    var mass by syncField(DATA_MASS, { body.setMass(it) })

    override fun baseTick() {
        super.baseTick()
        if (!level().isClientSide) {
            update {
                if (body.isActive) {
                    // 仅在刚体激活时才更新速度，节约带宽
                    angularVelocity = body.getAngularVelocity(null).toVec3()
                    linearVelocity = body.getLinearVelocity(null).toVec3()
                }
            }
        }
    }

    override fun recreateFromPacket(packet: ClientboundAddEntityPacket) {
        super.recreateFromPacket(packet)
        isKinematic = true // 客户端默认为运动学（运动由服务端权威下发）
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_GRAVITY, Vector3f())
        builder.define(DATA_IS_GRAVITY_PROTECTED, false)
        builder.define(DATA_ANGULAR_FACTOR, Vector3f(1f))
        builder.define(DATA_ANGULAR_VELOCITY, Vector3f())
        builder.define(DATA_ANGULAR_DUMPING, 0f)
        builder.define(DATA_ANGULAR_SLEEPING_THRESHOLD, 1f)
        builder.define(DATA_LINEAR_FACTOR, Vector3f(1f))
        builder.define(DATA_LINEAR_VELOCITY, Vector3f())
        builder.define(DATA_LINEAR_DUMPING, 0f)
        builder.define(DATA_LINEAR_SLEEPING_THRESHOLD, 0.8f)
        builder.define(DATA_INVERSE_INERTIA_LOCAL, Vector3f())
        builder.define(DATA_MASS, 1.0f)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        compound.ifContains("physics_isKinematic") { isKinematic = getBoolean(it) }
        compound.ifContains("physics_gravity") { gravity = getVec3(it) }
        compound.ifContains("physics_isGravityProtected") { isGravityProtected = getBoolean(it) }
        compound.ifContains("physics_angularFactor") { angularFactor = getVec3(it) }
        compound.ifContains("physics_angularVelocity") { angularVelocity = getVec3(it) }
        compound.ifContains("physics_angularDamping") { angularDamping = getFloat(it) }
        compound.ifContains("physics_angularSleepingThreshold") { angularSleepingThreshold = getFloat(it) }
        compound.ifContains("physics_linearFactor") { linearFactor = getVec3(it) }
        compound.ifContains("physics_linearVelocity") { linearVelocity = getVec3(it) }
        compound.ifContains("physics_linearDamping") { linearDamping = getFloat(it) }
        compound.ifContains("physics_linearSleepingThreshold") { linearSleepingThreshold = getFloat(it) }
        compound.ifContains("physics_mass") { mass = getFloat(it) }
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
        compound.putFloat("physics_mass", mass)
    }

}