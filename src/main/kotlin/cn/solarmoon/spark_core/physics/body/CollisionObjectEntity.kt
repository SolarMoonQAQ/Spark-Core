package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.getVec3
import cn.solarmoon.spark_core.util.putVec3
import cn.solarmoon.spark_core.util.toDegrees
import cn.solarmoon.spark_core.util.toEuler
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVec3
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class CollisionObjectEntity(
    type: EntityType<*>,
    level: Level
): Entity(type, level) {

    companion object {
        val DATA_ZROT = SynchedEntityData.defineId(CollisionObjectEntity::class.java, EntityDataSerializers.FLOAT)
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

    var zRot0 = 0f
    
    abstract var zRot: Float
    abstract var rotation: Vec3
    abstract var scale: Vec3
    
    var anisotropicFriction by syncField(DATA_ANISOTROPIC_FRICTION, { it.toVec3() }, { it.toVector3f() }, { body.setAnisotropicFriction(it.toBVector3f(), 0) })
    var collisionGroup by syncField(DATA_COLLISION_GROUP, { it }, { it }, { body.collisionGroup = it })
    var collideWithGroups by syncField(DATA_COLLISION_WITH_GROUPS, { it }, { it }, { body.collideWithGroups = it })
    var ccdMotionThreshold by syncField(DATA_CCD_MOTION_THRESHOLD, { it }, { it }, { body.ccdMotionThreshold = it })
    var ccdSweptSphereRadius by syncField(DATA_CCD_SWEEP_SPHERE_RADIUS, { it }, { it }, { body.ccdSweptSphereRadius = it })
    var ccdSquareMotionThreshold by syncField(DATA_CCD_SQUARE_MOTION_THRESHOLD, { it }, { it })
    var contactStiffness by syncField(DATA_CONTACT_STIFFNESS, { it }, { it }, { body.contactStiffness = it })
    var contactDamping by syncField(DATA_CONTACT_DAMPING, { it }, { it }, { body.contactDamping = it })
    var contactProcessingThreshold by syncField(DATA_CONTACT_PROCESSING_THRESHOLD, { it }, { it }, { body.contactProcessingThreshold = it })
    var friction by syncField(DATA_FRICTION, { it }, { it }, { body.friction = it })
    var rollingFriction by syncField(DATA_ROLLING_FRICTION, { it }, { it }, { body.rollingFriction = it })
    var spinningFriction by syncField(DATA_SPINNING_FRICTION, { it }, { it }, { body.spinningFriction = it })
    var deactivationTime by syncField(DATA_DEACTIVATION_TIME, { it }, { it }, { body.deactivationTime = it })
    var restitution by syncField(DATA_RESTITUTION, { it }, { it }, { body.restitution = it })
    var activationState by syncField(DATA_ACTIVATION_STATE, { it }, { it }, needAutoUpdate = true)
    var isContactResponse by syncField(DATA_IS_CONTACT_RESPONSE, { it }, { it }, needAutoUpdate = true)
    var isActive by syncField(DATA_IS_ACTIVE, { it }, { it }, { body.activate(it) })
    val isInWorld get() = body.isInWorld
    val isStatic get() = body.isStatic
    val isColliding get() = body.isColliding

    override fun setOldPosAndRot() {
        super.setOldPosAndRot()
        zRot0 = zRot
    }

    override fun baseTick() {
        super.baseTick()
        zRot0 = zRot

        if (!level().isClientSide) {
            // 由刚体驱动变换，其它属性可从entity设置
            update {
                setPos(body.getPhysicsLocation(null).toVec3())
                rotation = body.getPhysicsRotation(null).toQuaternionf().toEuler().toDegrees().toVec3()
                activationState = body.activationState
                isContactResponse = body.isContactResponse
            }
        }
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_ZROT, body.getPhysicsRotation(null).toQuaternionf().toEuler().toDegrees().z)
        builder.define(DATA_SCALE, body.getScale(null).toVector3f())
        builder.define(DATA_ANISOTROPIC_FRICTION, body.getAnisotropicFriction(null).toVector3f())
        builder.define(DATA_COLLISION_GROUP, body.collisionGroup)
        builder.define(DATA_COLLISION_WITH_GROUPS, body.collideWithGroups)
        builder.define(DATA_CCD_MOTION_THRESHOLD, body.ccdMotionThreshold)
        builder.define(DATA_CCD_SWEEP_SPHERE_RADIUS, body.ccdSweptSphereRadius)
        builder.define(DATA_CCD_SQUARE_MOTION_THRESHOLD, body.ccdSquareMotionThreshold)
        builder.define(DATA_CONTACT_STIFFNESS, body.contactStiffness)
        builder.define(DATA_CONTACT_DAMPING, body.contactDamping)
        builder.define(DATA_CONTACT_PROCESSING_THRESHOLD, body.contactProcessingThreshold)
        builder.define(DATA_FRICTION, body.friction)
        builder.define(DATA_ROLLING_FRICTION, body.rollingFriction)
        builder.define(DATA_SPINNING_FRICTION, body.spinningFriction)
        builder.define(DATA_DEACTIVATION_TIME, body.deactivationTime)
        builder.define(DATA_RESTITUTION, body.restitution)
        builder.define(DATA_ACTIVATION_STATE, body.activationState)
        builder.define(DATA_IS_CONTACT_RESPONSE, body.isContactResponse)
        builder.define(DATA_IS_ACTIVE, body.isActive)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        zRot = compound.getFloat("physics_zRot")
        scale = compound.getVec3("physics_scale")
        anisotropicFriction = compound.getVec3("physics_anisotropicFriction")
        collisionGroup = compound.getInt("physics_collisionGroup")
        collideWithGroups = compound.getInt("physics_collideWithGroups")
        ccdMotionThreshold = compound.getFloat("physics_ccdMotionThreshold")
        ccdSweptSphereRadius = compound.getFloat("physics_ccdSweptSphereRadius")
        ccdSquareMotionThreshold = compound.getFloat("physics_ccdSquareMotionThreshold")
        contactStiffness = compound.getFloat("physics_contactStiffness")
        contactDamping = compound.getFloat("physics_contactDamping")
        contactProcessingThreshold = compound.getFloat("physics_contactProcessingThreshold")
        friction = compound.getFloat("physics_friction")
        rollingFriction = compound.getFloat("physics_rollingFriction")
        spinningFriction = compound.getFloat("physics_spinningFriction")
        deactivationTime = compound.getFloat("physics_deactivationTime")
        restitution = compound.getFloat("physics_restitution")
        activationState = compound.getInt("physics_activationState")
        isContactResponse = compound.getBoolean("physics_isContactResponse")
        isActive = compound.getBoolean("physics_isActive")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putFloat("physics_zRot", zRot)
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
        compound.putBoolean("physics_isActive", isActive)
    }

    protected fun update(action: () -> Unit) {
        updating = true
        action()
        updating = false
    }

    /**
     * @param needAutoUpdate 比如刚体位置，会在baseTick中从刚体本身获取，此时如果使用了此参数的setter会导致对刚体位置重复赋值，因此将此项设为true以避免在自动更新时调用sumbit提交任务到物理端
     *
     * 一个标准是：如果此值都是靠你后续手动配置的，那么就用默认false，如果此值需要每tick从body本身更新过来的，则用true
     *
     * 当然对于只读的值，也就是不需要[applyToBody]的情况，此值不影响逻辑
     */
    protected fun <A: Any, T> syncField(
        accessor: EntityDataAccessor<A>,
        transformFrom: (A) -> T,
        transformTo: (T) -> A,
        applyToBody: ((T) -> Unit)? = null,
        needAutoUpdate: Boolean = false
    ): ReadWriteProperty<CollisionObjectEntity, T> {
        return object : ReadWriteProperty<CollisionObjectEntity, T> {
            override fun getValue(thisRef: CollisionObjectEntity, property: KProperty<*>): T =
                transformFrom(thisRef.entityData.get(accessor))

            override fun setValue(thisRef: CollisionObjectEntity, property: KProperty<*>, value: T) {
                thisRef.entityData.set(accessor, transformTo(value))
                if (applyToBody != null) {
                    if (!needAutoUpdate || !thisRef.updating) {
                        thisRef.physicsLevel.submitImmediateTask {
                            applyToBody(value)
                        }
                    }
                }
            }
        }
    }

}