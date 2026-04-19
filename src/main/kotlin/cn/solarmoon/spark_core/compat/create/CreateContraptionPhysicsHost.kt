package cn.solarmoon.spark_core.compat.create

import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.body.CollisionGroups
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.util.toRadians
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.ContraptionCollider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import java.lang.ref.WeakReference

/**
 * Create 装置在 Spark 物理系统中的宿主对象
 *
 * 职责：
 * 1) 持有并管理装置对应的运动学刚体；
 * 2) 管理“子形状索引 -> 本地方块坐标”映射；
 * 3) 提供碰撞命中查询方法（按子形状ID / 按碰撞点位置）
 */
class CreateContraptionPhysicsHost(
    override val physicsLevel: PhysicsLevel
) : PhysicsHost {

    companion object {
        /**
         * 从接触法线方向回退的默认偏移量（世界坐标单位：方块）
         *
         * 该值用于边界点容错，避免因浮点误差落入空气块
         */
        private const val CONTACT_BACKOFF = 1.0e-3
    }

    /**
     * 物理宿主管理的所有碰撞体
     *
     * 当前实现仅使用一个主刚体：`contraption_body`
     */
    override val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject> = mutableMapOf()

    /**
     * 装置主刚体（运动学刚体）
     */
    val body: PhysicsRigidBody = PhysicsRigidBody(BoxCollisionShape(Vector3f(0.01f, 0.01f, 0.01f))).apply {
        name = "create_contraption_body"
        isKinematic = true
        friction = 0.8f
        restitution = 0.0f
        owner = this@CreateContraptionPhysicsHost
        collisionGroup = CollisionGroups.TERRAIN
        collideWithGroups = CollisionGroups.NONE
        shouldShowDebugBoxWhenNonColldeWith = true
    }

    /**
     * 同步状态缓存
     */
    val syncState = CreateContraptionSyncState()

    /**
     * 当前绑定的装置对象
     */
    var currentContraption: Contraption? = null
        private set

    /**
     * 当前绑定的装置实体弱引用。
     *
     * 设计原因：
     * - 避免 Host 强持有实体导致世界卸载后对象无法回收；
     * - 供世界点反算接口直接获取实体姿态，不再依赖全局 `entityId -> entity` 查询。
     */
    private var entityRef: WeakReference<AbstractContraptionEntity>? = null

    /**
     * 子形状索引到本地方块坐标映射
     */
    private var childShapeToLocalBlock: Map<Int, BlockPos> = emptyMap()

    /**
     * 本地方块坐标到方块状态映射
     */
    private var localBlockStates: Map<BlockPos, BlockState> = emptyMap()

    /**
     * 将宿主标记为“碰撞形状待重建”
     */
    fun markShapeDirty() {
        syncState.markShapeDirty()
    }

    /**
     * 绑定（或刷新）当前装置实体弱引用。
     *
     * 线程语义：
     * - 可在主线程事件回调与物理同步回调中重复调用；
     * - 操作仅涉及引用替换，无重型计算。
     */
    fun bindEntity(entity: AbstractContraptionEntity) {
        entityRef = WeakReference(entity)
    }

    /**
     * 每次主线程与物理线程同步时，在物理线程中执行一次装置同步
     *
     * 逻辑顺序：
     * 1) 绑定当前 contraption 引用，必要时触发脏标记；
     * 2) 按脏标记重建碰撞形状与索引映射；
     * 3) 更新刚体位姿（每次同步都更新）
     */
    fun onPhysicsSyncTick(entity: AbstractContraptionEntity) {
        // 每次物理同步都刷新一次实体弱引用，保证查询接口拿到当前实例。
        bindEntity(entity)
        val contraption = entity.contraption ?: return
        currentContraption = contraption

        if (syncState.bindContraption(contraption)) {
            // 装置实例切换时，更新“contraption -> host”注册关系并强制重建形状
            CreateContraptionPhysicsApplier.bindContraption(contraption, this)
        }

        if (syncState.consumeShapeDirty()) {
            rebuildShape(contraption)
        }

        // 每次同步周期都更新一次位姿
        val anchor = entity.anchorVec
        val bodyOrigin = anchor.add(0.5, 0.5, 0.5)
        val rotationState = entity.rotationState
        val rotation = Quaternionf().rotateZYX(
            rotationState.zRotation.toRadians(),
            rotationState.yRotation.toRadians(),
            rotationState.xRotation.toRadians())
        body.setPhysicsLocation(bodyOrigin.toBVector3f())
        body.setPhysicsRotation(rotation.toBQuaternion())
        body.activate()
        syncState.updateLastTransform(
            bodyOrigin,
            rotationState.xRotation,
            rotationState.yRotation,
            rotationState.zRotation,
            rotationState.secondYRotation
        )
    }

    /**
     * 根据子形状 ID 查询命中的方块坐标（装置本地坐标）
     */
    fun getContactBlockPosByChildShapeId(childShapeId: Int): BlockPos? {
        return childShapeToLocalBlock[childShapeId]
    }

    /**
     * 根据子形状 ID 查询命中的 BlockState
     */
    fun getContactBlockStateByChildShapeId(childShapeId: Int): BlockState? {
        val blockPos = getContactBlockPosByChildShapeId(childShapeId) ?: return null
        return localBlockStates[blockPos]
    }

    /**
     * 根据世界碰撞点查询命中的方块坐标（装置本地坐标）
     *
     * 回退策略：
     * - 先直接将世界点转换为 local 并取方块坐标；
     * - 若未命中且存在法线，则沿法线方向回退微小距离后再次转换
     */
    fun getContactBlockPosByWorldPoint(worldPoint: Vec3, contactNormal: Vec3?): BlockPos? {
        val contraptionEntity = entityRef?.get() ?: return null
        val localPos = ContraptionCollider.worldToLocalPos(worldPoint, contraptionEntity)
        val directBlockPos = BlockPos.containing(localPos.x, localPos.y, localPos.z)
        if (localBlockStates.containsKey(directBlockPos)) {
            return directBlockPos
        }
        val normal = contactNormal ?: return null
        if (normal.lengthSqr() <= 1.0e-8) return null

        // 使用接触法线回退，优先命中入射面的方块
        val adjusted = worldPoint.subtract(normal.normalize().scale(CONTACT_BACKOFF))
        val adjustedLocal = ContraptionCollider.worldToLocalPos(adjusted, contraptionEntity)
        val fallbackBlockPos = BlockPos.containing(adjustedLocal.x, adjustedLocal.y, adjustedLocal.z)
        return fallbackBlockPos.takeIf { localBlockStates.containsKey(it) }
    }

    /**
     * 根据世界碰撞点查询命中的 BlockState
     */
    fun getContactBlockStateByWorldPoint(worldPoint: Vec3, contactNormal: Vec3?): BlockState? {
        val blockPos = getContactBlockPosByWorldPoint(worldPoint, contactNormal) ?: return null
        return localBlockStates[blockPos]
    }

    /**
     * 在物理线程中重建装置碰撞形状与索引映射
     */
    private fun rebuildShape(contraption: Contraption) {
        val result = CreateContraptionShapeBuilder.build(contraption)
        body.collisionShape = result.shape
        childShapeToLocalBlock = result.childShapeToLocalBlock
        localBlockStates = result.localBlockStates
    }
}
