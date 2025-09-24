package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.delta_sync.DiffSyncField
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.physics.CollisionGroups
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.BlockCollisionHelper
import cn.solarmoon.spark_core.physics.sync.PhysicsComponentPayload
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.InlineEventConsumer
import cn.solarmoon.spark_core.util.InlineEventHandler
import cn.solarmoon.spark_core.util.toQuaternionf
import cn.solarmoon.spark_core.util.toVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.joints.PhysicsJoint
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3i
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class CollisionObjectComponent<B: PhysicsCollisionObject>(
    val name: String,
    val authority: Authority,
    val level: Level,
    val diffSyncSchema: DiffSyncSchema<out CollisionObjectComponent<*>>,
    val type: CollisionObjectType<out CollisionObjectComponent<*>>,
    val body: B
): InlineEventHandler<CollisionObjectEvent> {

    override val eventHandlers: MutableMap<KClass<out CollisionObjectEvent>, MutableList<InlineEventConsumer<out CollisionObjectEvent>>> = mutableMapOf()

    var owner: PhysicsHost? = null
        internal set

    init {
        body.userObject = this
    }

    private var isUpdating = false

    abstract var position: Vector3f
    abstract var rotation: Quaternionf
    abstract var scale: Vector3f
    @DiffSyncField var anisotropicFriction = body.getAnisotropicFriction(null).toVector3f()
    @DiffSyncField var collisionGroup by setterField(body.collisionGroup) { body.collisionGroup = it }
    @DiffSyncField var collideWithGroups by setterField(body.collideWithGroups) { body.collideWithGroups = it }
    @DiffSyncField var ccdMotionThreshold by setterField(body.ccdMotionThreshold) { body.ccdMotionThreshold = it }
    @DiffSyncField var ccdSweptSphereRadius by setterField(body.ccdSweptSphereRadius) { body.ccdSweptSphereRadius = it }
    @DiffSyncField var ccdSquareMotionThreshold = body.ccdSquareMotionThreshold
    @DiffSyncField var contactStiffness by setterField(body.contactStiffness) { body.contactStiffness = it }
    @DiffSyncField var contactDamping by setterField(body.contactDamping) { body.contactDamping = it }
    @DiffSyncField var contactProcessingThreshold by setterField(body.contactProcessingThreshold) { body.contactProcessingThreshold = it }
    @DiffSyncField var friction by setterField(body.friction) { body.friction = it }
    @DiffSyncField var rollingFriction by setterField(body.rollingFriction) { body.rollingFriction = it }
    @DiffSyncField var spinningFriction by setterField(body.spinningFriction) { body.spinningFriction = it }
    @DiffSyncField var deactivationTime by setterField(body.deactivationTime) { body.deactivationTime = it }
    @DiffSyncField var restitution by setterField(body.restitution) { body.restitution = it }
    @DiffSyncField var activationState = body.activationState
    @DiffSyncField var isContactResponse = body.isContactResponse
    @DiffSyncField var isActive = body.isActive
    @DiffSyncField var isInWorld = body.isInWorld
    @DiffSyncField var isStatic = body.isStatic
    var lastTransform = body.getTransform(null)
    var transform = body.getTransform(null)
    var boundingBox = body.boundingBox(null)
    var isColliding = false
    @DiffSyncField var collideWithOwnerGroups = CollisionGroups.NONE

    val id get() = body.nativeId()
    val physicsLevel get() = level.physicsLevel

    /**
     * 在当前权威线程中更新物理线程计算结果
     */
    open fun update() {
        position = body.getPhysicsLocation(null).toVector3f()
        rotation = body.getPhysicsRotation(null).toQuaternionf()
        scale = body.getScale(null).toVector3f()
        anisotropicFriction = body.getAnisotropicFriction(null).toVector3f()
        collisionGroup = body.collisionGroup
        collideWithGroups = body.collideWithGroups
        ccdMotionThreshold = body.ccdMotionThreshold
        ccdSweptSphereRadius = body.ccdSweptSphereRadius
        ccdSquareMotionThreshold = body.ccdSquareMotionThreshold
        contactStiffness = body.contactStiffness
        contactDamping = body.contactDamping
        contactProcessingThreshold = body.contactProcessingThreshold
        friction = body.friction
        rollingFriction = body.rollingFriction
        spinningFriction = body.spinningFriction
        deactivationTime = body.deactivationTime
        restitution = body.restitution
        activationState = body.activationState
        isContactResponse = body.isContactResponse
        isActive = body.isActive
        isInWorld = body.isInWorld
        isStatic = body.isStatic

        boundingBox = body.boundingBox(null)
        lastTransform = transform
        transform = body.getTransform(null)
    }

    open fun tick() {
        if (authority.isInRightSide(level)) {
            when (authority) {
                // 服务器权威时，在服务端更新数据，然后同步差异数据
                Authority.SERVER -> {
                    (diffSyncSchema as DiffSyncSchema<CollisionObjectComponent<*>>).snapshotFrom(this).let { lastData ->
                        isUpdating = true
                        update()
                        isUpdating = false
                        diffSyncSchema.diffPacket(lastData, this).ifValid {
                            PacketDistributor.sendToAllPlayers(PhysicsComponentPayload(id, this))
                        }
                    }
                }
                // 客户端权威时，在客户端更新数据
                Authority.CLIENT -> {
                    isUpdating = true
                    update()
                    isUpdating = false
                }
            }

            if (!body.isStatic && body.isActive) {
                if ((body.collideWithGroups and CollisionGroups.TERRAIN != 0)) BlockCollisionHelper.addNearbyTerrainBlocksToWorld(body, physicsLevel)
            } else if (body.collisionGroup == CollisionGroups.TERRAIN && body is PhysicsRigidBody) {
                if (body.userIndex() < 0) {//移除过久未被访问的块记录及其刚体对象
                    physicsLevel.world.remove(body)
                    physicsLevel.terrainBlockBodies.remove(BlockPos(position.toVec3i()))
                } else body.setUserIndex(body.userIndex() - 1) //销毁倒计时推进
            }
        } else {
            isUpdating = true
            update()
            isUpdating = false
        }
    }

    open fun physicsTick() {}

    /**
     * 绑定到宿主，没有额外逻辑
     */
    fun bindHost(owner: PhysicsHost) {
        unbindHost()
        this.owner = owner
        owner.allCollisionObjects[name] = this
    }

    fun unbindHost() {
        owner?.allCollisionObjects?.remove(name)
        this.owner = null
    }

    fun addJoint(joint: PhysicsJoint) {
        physicsLevel.submitImmediateTask {
            physicsLevel.world.addJoint(joint)
        }
    }

    fun removeJoint(joint: PhysicsJoint) {
        physicsLevel.submitImmediateTask {
            physicsLevel.world.removeJoint(joint)
        }
    }

    fun addToLevel() {
        level.addCollisionComponent(this)
        if (!body.isInWorld) {
            physicsLevel.submitImmediateTask {
                if (authority == Authority.SERVER && level.isClientSide) {
                    if (body is PhysicsRigidBody) body.isKinematic = true // 服务端权威的刚体在客户端中只能为运动学（不在客户端主动运动，由服务端同步运动）
                }
                physicsLevel.world.addCollisionObject(body)
            }
        }
    }

    fun remove() {
        unbindHost()
        level.removeCollisionComponent(this)
        physicsLevel.submitImmediateTask {
            physicsLevel.world.removeCollisionObject(body)
        }
    }

    fun setAnisotropicFriction(value: Vector3f, mode: Int) {
        physicsLevel.submitImmediateTask {
            body.setAnisotropicFriction(value.toBVector3f(), mode)
        }
    }

    protected fun <T> setterField(initial: T, applyToBody: (T) -> Unit): ReadWriteProperty<CollisionObjectComponent<*>, T> {
        return object : ReadWriteProperty<CollisionObjectComponent<*>, T> {
            private var backing = initial

            override fun getValue(thisRef: CollisionObjectComponent<*>, property: KProperty<*>): T = backing

            override fun setValue(thisRef: CollisionObjectComponent<*>, property: KProperty<*>, value: T) {
                if (isUpdating) backing = value
                else level.submitImmediateTask {
                    applyToBody(value)
                }
            }
        }
    }

}