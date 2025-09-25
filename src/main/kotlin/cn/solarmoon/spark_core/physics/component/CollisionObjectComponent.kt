package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffSnapshot
import cn.solarmoon.spark_core.delta_sync.DiffSyncField
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.physics.CollisionGroups
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.BlockCollisionHelper
import cn.solarmoon.spark_core.physics.component.shape.CollisionShapeType
import cn.solarmoon.spark_core.physics.sync.CreatePhysicsBodyPayload
import cn.solarmoon.spark_core.physics.sync.PhysicsComponentPayload
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.registry.common.SparkCollisionShapeTypes
import cn.solarmoon.spark_core.util.*
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.joints.PhysicsJoint
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3i
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class CollisionObjectComponent<B : PhysicsCollisionObject>(
    val name: String,
    val authority: Authority,
    val level: Level,
    val diffSyncSchema: DiffSyncSchema<out CollisionObjectComponent<*>>,
    val type: CollisionObjectType<out CollisionObjectComponent<*>>,
    val body: B
) : InlineEventHandler<CollisionObjectEvent> {

    override val eventHandlers: MutableMap<KClass<out CollisionObjectEvent>, MutableList<InlineEventConsumer<out CollisionObjectEvent>>> =
        mutableMapOf()

    var owner: PhysicsHost? = null
        internal set

    init {
        body.userObject = this
    }

    private var isUpdating = false

    abstract var position: Vector3f
    abstract var rotation: Quaternionf
    abstract var scale: Vector3f
    @DiffSyncField
    var anisotropicFriction = body.getAnisotropicFriction(null).toVector3f()
    @DiffSyncField
    var collisionGroup by setterField(body.collisionGroup) { body.collisionGroup = it }
    @DiffSyncField
    var collideWithGroups by setterField(body.collideWithGroups) { body.collideWithGroups = it }
    @DiffSyncField
    var ccdMotionThreshold by setterField(body.ccdMotionThreshold) { body.ccdMotionThreshold = it }
    @DiffSyncField
    var ccdSweptSphereRadius by setterField(body.ccdSweptSphereRadius) { body.ccdSweptSphereRadius = it }
    @DiffSyncField
    var ccdSquareMotionThreshold = body.ccdSquareMotionThreshold
    @DiffSyncField
    var contactStiffness by setterField(body.contactStiffness) { body.contactStiffness = it }
    @DiffSyncField
    var contactDamping by setterField(body.contactDamping) { body.contactDamping = it }
    @DiffSyncField
    var contactProcessingThreshold by setterField(body.contactProcessingThreshold) {
        body.contactProcessingThreshold = it
    }
    @DiffSyncField
    var friction by setterField(body.friction) { body.friction = it }
    @DiffSyncField
    var rollingFriction by setterField(body.rollingFriction) { body.rollingFriction = it }
    @DiffSyncField
    var spinningFriction by setterField(body.spinningFriction) { body.spinningFriction = it }
    @DiffSyncField
    var deactivationTime by setterField(body.deactivationTime) { body.deactivationTime = it }
    @DiffSyncField
    var restitution by setterField(body.restitution) { body.restitution = it }
    @DiffSyncField
    var activationState = body.activationState
    @DiffSyncField
    var isContactResponse = body.isContactResponse
    @DiffSyncField
    var isActive = body.isActive
    @DiffSyncField
    var isInWorld = body.isInWorld
    @DiffSyncField
    var isStatic = body.isStatic
    @DiffSyncField
    var userIndex by setterField(body.userIndex()) { body.setUserIndex(it) }
    @DiffSyncField
    var userIndex2 by setterField(body.userIndex2()) { body.setUserIndex2(it) }
    @DiffSyncField
    var userIndex3 by setterField(body.userIndex3()) { body.setUserIndex3(it) }
    var lastTransform = body.getTransform(null)
    var transform = body.getTransform(null)
    var boundingBox = body.boundingBox(null)
    var isColliding = false
    @DiffSyncField
    var collideWithOwnerGroups = CollisionGroups.NONE
    @DiffSyncField
    var shapeType: CollisionShapeType<*> = SparkCollisionShapeTypes.DEFAULT_BOX.get()
        set(value) {
            field = value
            physicsLevel.submitImmediateTask {
                body.collisionShape = value.create(this)
            }
        }

    var id = if (authority == Authority.SERVER) body.nativeId() else -body.nativeId()
        internal set
    val physicsLevel get() = level.physicsLevel

    private var lastData: DiffSnapshot? = null

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
        userIndex = body.userIndex()
        userIndex2 = body.userIndex2()
        userIndex3 = body.userIndex3()

        boundingBox = body.boundingBox(null)
        lastTransform = transform
        transform = Transform(position.toBVector3f(), rotation.toBQuaternion(), scale.toBVector3f())
    }

    open fun tick() {
        if (authority.isInRightSide(level)) {
            when (authority) {
                // 服务器权威时，在服务端更新数据，然后同步差异数据
                Authority.SERVER -> {
                    isUpdating = true
                    update()
                    isUpdating = false
                    (diffSyncSchema as DiffSyncSchema<CollisionObjectComponent<*>>).diffPacket(lastData!!, this)
                        .ifValid {
                            PacketDistributor.sendToAllPlayers(PhysicsComponentPayload(id, this))
                        }
                    updateData()
                }
                // 客户端权威时，在客户端更新数据
                Authority.CLIENT -> {
                    isUpdating = true
                    update()
                    isUpdating = false
                }
                // 双端各自处理
                Authority.BOTH -> {
                    isUpdating = true
                    update()
                    isUpdating = false
                }
            }
        } else {
            isUpdating = true
            update()
            isUpdating = false
        }

        if (!body.isStatic && body.isActive) {
            //收集非地形方块刚体周边的方块，用于创建地形碰撞
            if ((body.collideWithGroups and CollisionGroups.TERRAIN != 0)) BlockCollisionHelper.gatherNearbyTerrainBlocksForWorld(
                body,
                physicsLevel
            )
        } else if (body.collisionGroup == CollisionGroups.TERRAIN && body is PhysicsRigidBody) {
            //衰减地形刚体寿命
            if (userIndex < 0) {//移除过久未被访问的块记录及其刚体对象
                physicsLevel.terrainBlockBodies.remove(BlockPos(position.floor().toVec3i()))
                remove()
            } else userIndex -= 1 //销毁倒计时推进
        }
    }

    open fun physicsTick() {}

    fun updateData() {
        lastData = (diffSyncSchema as DiffSyncSchema<CollisionObjectComponent<*>>).snapshotFrom(this)
    }

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
        if (!body.isInWorld && authority.isInRightSide(level)) {
            level.addCollisionComponent(this)
            if (authority == Authority.SERVER) {
                PacketDistributor.sendToAllPlayers(CreatePhysicsBodyPayload(id, name, authority, type))
            }
            physicsLevel.submitImmediateTask {
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

    protected fun <T> setterField(
        initial: T,
        applyToBody: (T) -> Unit
    ): ReadWriteProperty<CollisionObjectComponent<*>, T> {
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