package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.getQuaternionf
import cn.solarmoon.spark_core.util.getVec3
import cn.solarmoon.spark_core.util.ifContains
import cn.solarmoon.spark_core.util.putQuaternionf
import cn.solarmoon.spark_core.util.putVec3
import cn.solarmoon.spark_core.util.setZ
import cn.solarmoon.spark_core.util.toDegrees
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class CollisionObjectEntity(
    type: EntityType<*>,
    level: Level
): Entity(type, level) {

    companion object {
        val DATA_ROTATION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.QUATERNION)
        val DATA_SCALE = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_ANISOTROPIC_FRICTION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.VECTOR3)
        val DATA_COLLISION_GROUP = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.INT)
        val DATA_COLLISION_WITH_GROUPS = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.INT)
        val DATA_CCD_MOTION_THRESHOLD = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_CCD_SWEEP_SPHERE_RADIUS = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_CCD_SQUARE_MOTION_THRESHOLD = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_CONTACT_STIFFNESS = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_CONTACT_DAMPING = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_CONTACT_PROCESSING_THRESHOLD = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_FRICTION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_ROLLING_FRICTION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_SPINNING_FRICTION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_DEACTIVATION_TIME = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_RESTITUTION = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
        val DATA_ACTIVATION_STATE = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.INT)
        val DATA_IS_CONTACT_RESPONSE = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DATA_IS_ACTIVE = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.BOOLEAN)
    }

    abstract val body: PhysicsCollisionObject

    protected var updating = false

    private var lerpPosition = Vec3.ZERO
    private var lerpViewAngles = Vec2.ZERO
    private var lerpSteps = 0

    var rotationO = Quaternionf()
    abstract var rotation: Quaternionf
    var scaleO = Vec3.ZERO
    abstract var scale: Vec3
    
    var anisotropicFriction by syncField(DATA_ANISOTROPIC_FRICTION, { it.toVec3() }, { it.toVector3f() }, { body.setAnisotropicFriction(it.toBVector3f(), 0) })
    var collisionGroup by syncField(DATA_COLLISION_GROUP) { body.collisionGroup = it }
    var collideWithGroups by syncField(DATA_COLLISION_WITH_GROUPS) { body.collideWithGroups = it }
    var ccdMotionThreshold by syncField(DATA_CCD_MOTION_THRESHOLD) { body.ccdMotionThreshold = it }
    var ccdSweptSphereRadius by syncField(DATA_CCD_SWEEP_SPHERE_RADIUS) { body.ccdSweptSphereRadius = it }
    var ccdSquareMotionThreshold by syncField(DATA_CCD_SQUARE_MOTION_THRESHOLD, { it }, { it })
    var contactStiffness by syncField(DATA_CONTACT_STIFFNESS) { body.contactStiffness = it }
    var contactDamping by syncField(DATA_CONTACT_DAMPING) { body.contactDamping = it }
    var contactProcessingThreshold by syncField(DATA_CONTACT_PROCESSING_THRESHOLD) { body.contactProcessingThreshold = it }
    var friction by syncField(DATA_FRICTION) { body.friction = it }
    var rollingFriction by syncField(DATA_ROLLING_FRICTION) { body.rollingFriction = it }
    var spinningFriction by syncField(DATA_SPINNING_FRICTION) { body.spinningFriction = it }
    var deactivationTime by syncField(DATA_DEACTIVATION_TIME) { body.deactivationTime = it }
    var restitution by syncField(DATA_RESTITUTION) { body.restitution = it }
    var activationState by syncField(DATA_ACTIVATION_STATE)
    var isContactResponse by syncField(DATA_IS_CONTACT_RESPONSE)
    var isActive by syncField(DATA_IS_ACTIVE) { body.activate(it) }
    val isInWorld get() = body.isInWorld
    val isStatic get() = body.isStatic
    val isColliding get() = body.isColliding

    override fun baseTick() {
        super.baseTick()

        if (!level().isClientSide) {
            // 由刚体驱动变换，其它属性可从entity设置
            update {
                setPos(body.getPhysicsLocation(null).toVec3())
                rotation = body.getPhysicsRotation(null).toQuaternionf()
                activationState = body.activationState
                isContactResponse = body.isContactResponse
            }
        } else {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpPosition.x, lerpPosition.y, lerpPosition.z, lerpViewAngles.y.toDouble(), lerpViewAngles.x.toDouble())
                lerpSteps--
            }
        }
    }

    override fun setOldPosAndRot() {
        super.setOldPosAndRot()
        rotationO = rotation
        scaleO = scale
    }

    override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {
        lerpPosition = Vec3(x, y, z)
        lerpViewAngles = Vec2(xRot, yRot)
        lerpSteps = steps
    }

    override fun lerpTargetX(): Double {
        return if (lerpSteps > 0) lerpPosition.x else x
    }

    override fun lerpTargetY(): Double {
        return if (lerpSteps > 0) lerpPosition.y else y
    }

    override fun lerpTargetZ(): Double {
        return if (lerpSteps > 0) lerpPosition.z else z
    }

    override fun lerpTargetXRot(): Float {
        return if (lerpSteps > 0) lerpViewAngles.x.toFloat() else xRot
    }

    override fun lerpTargetYRot(): Float {
        return if (lerpSteps > 0) lerpViewAngles.y.toFloat() else yRot
    }

    override fun onAddedToLevel() {
        super.onAddedToLevel()
        level().addPhysicsBody(body)
    }

    override fun onRemovedFromLevel() {
        super.onRemovedFromLevel()
        level().removePhysicsBody(body)
    }

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        when (key) {
            DATA_ROTATION -> rotation = rotation // 旋转同步时只更新了字段，不会调用setter方法，这里再主动调用一下
        }
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_ROTATION, Quaternionf().identity())
        builder.define(DATA_SCALE, Vector3f(1f))
        builder.define(DATA_ANISOTROPIC_FRICTION, Vector3f(1f))
        builder.define(DATA_COLLISION_GROUP, 0)
        builder.define(DATA_COLLISION_WITH_GROUPS, 0)
        builder.define(DATA_CCD_MOTION_THRESHOLD, 0f)
        builder.define(DATA_CCD_SWEEP_SPHERE_RADIUS, 0f)
        builder.define(DATA_CCD_SQUARE_MOTION_THRESHOLD, 0f)
        builder.define(DATA_CONTACT_STIFFNESS, 1e18f)
        builder.define(DATA_CONTACT_DAMPING, 0.1f)
        builder.define(DATA_CONTACT_PROCESSING_THRESHOLD, 1e18f)
        builder.define(DATA_FRICTION, 0.5f)
        builder.define(DATA_ROLLING_FRICTION, 0f)
        builder.define(DATA_SPINNING_FRICTION, 0f)
        builder.define(DATA_DEACTIVATION_TIME, 0f)
        builder.define(DATA_RESTITUTION, 0f)
        builder.define(DATA_ACTIVATION_STATE, 0)
        builder.define(DATA_IS_CONTACT_RESPONSE, false)
        builder.define(DATA_IS_ACTIVE, false)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        compound.ifContains("physics_rotation") { rotation = getQuaternionf(it) }
        compound.ifContains("physics_scale") { scale = getVec3(it) }
        compound.ifContains("physics_anisotropicFriction") { anisotropicFriction = getVec3(it) }
        compound.ifContains("physics_collisionGroup") { collisionGroup = getInt(it) }
        compound.ifContains("physics_collideWithGroups") { collideWithGroups = getInt(it) }
        compound.ifContains("physics_ccdMotionThreshold") { ccdMotionThreshold = getFloat(it) }
        compound.ifContains("physics_ccdSweptSphereRadius") { ccdSweptSphereRadius = getFloat(it) }
        compound.ifContains("physics_ccdSquareMotionThreshold") { ccdSquareMotionThreshold = getFloat(it) }
        compound.ifContains("physics_contactStiffness") { contactStiffness = getFloat(it) }
        compound.ifContains("physics_contactDamping") { contactDamping = getFloat(it) }
        compound.ifContains("physics_contactProcessingThreshold") { contactProcessingThreshold = getFloat(it) }
        compound.ifContains("physics_friction") { friction = getFloat(it) }
        compound.ifContains("physics_rollingFriction") { rollingFriction = getFloat(it) }
        compound.ifContains("physics_spinningFriction") { spinningFriction = getFloat(it) }
        compound.ifContains("physics_deactivationTime") { deactivationTime = getFloat(it) }
        compound.ifContains("physics_restitution") { restitution = getFloat(it) }
        compound.ifContains("physics_activationState") { activationState = getInt(it) }
        compound.ifContains("physics_isContactResponse") { isContactResponse = getBoolean(it) }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putQuaternionf("physics_rotation", rotation)
        compound.putVec3("physics_scale", scale)
        compound.putVec3("physics_anisotropicFriction", anisotropicFriction)
        compound.putInt("physics_collisionGroup", collisionGroup)
        compound.putInt("physics_collideWithGroups", collideWithGroups)
        compound.putFloat("physics_ccdMotionThreshold", ccdMotionThreshold)
        compound.putFloat("physics_ccdSweptSphereRadius", ccdSweptSphereRadius)
        compound.putFloat("physics_ccdSquareMotionThreshold", ccdSquareMotionThreshold)
        compound.putFloat("physics_contactStiffness", contactStiffness)
        compound.putFloat("physics_contactDamping", contactDamping)
        compound.putFloat("physics_contactProcessingThreshold", contactProcessingThreshold)
        compound.putFloat("physics_friction", friction)
        compound.putFloat("physics_rollingFriction", rollingFriction)
        compound.putFloat("physics_spinningFriction", spinningFriction)
        compound.putFloat("physics_deactivationTime", deactivationTime)
        compound.putFloat("physics_restitution", restitution)
        compound.putInt("physics_activationState", activationState)
        compound.putBoolean("physics_isContactResponse", isContactResponse)
    }

    protected fun update(action: () -> Unit) {
        updating = true
        action()
        updating = false
    }

    protected fun <A: Any> syncField(accessor: EntityDataAccessor<A>, applyToBody: ((A) -> Unit)? = null) = syncField(accessor, { it }, { it }, applyToBody)

    /**
     * @param needAutoUpdate 比如刚体位置，会在baseTick中从刚体本身获取，此时如果使用了此参数的setter会导致对刚体位置重复赋值，因此将此项设为true以避免在自动更新时调用sumbit提交任务到物理端
     *
     * 一个标准是：如果此值都是靠你后续手动配置的，那么就用默认false，如果此值需要每tick从body本身更新过来的，则用true
     *
     * 当然对于只读的值，也就是不需要[applyToBody]的情况，此值不影响逻辑
     */
    protected fun <A: Any, T> syncField(
        accessor: EntityDataAccessor<A>,
        getter: (A) -> T,
        setter: (T) -> A,
        applyToBody: ((T) -> Unit)? = null
    ): ReadWriteProperty<CollisionObjectEntity, T> {
        return object : ReadWriteProperty<CollisionObjectEntity, T> {
            override fun getValue(thisRef: CollisionObjectEntity, property: KProperty<*>): T =
                getter(thisRef.entityData.get(accessor))

            override fun setValue(thisRef: CollisionObjectEntity, property: KProperty<*>, value: T) {
                thisRef.entityData.set(accessor, setter(value))
                if (applyToBody != null) {
                    if (!thisRef.updating) {
                        thisRef.physicsLevel.submitImmediateTask {
                            applyToBody(value)
                        }
                    }
                }
            }
        }
    }

}