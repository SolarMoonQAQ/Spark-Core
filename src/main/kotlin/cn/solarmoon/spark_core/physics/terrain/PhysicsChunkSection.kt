package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.CollisionGroups
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.body.addPhysicsBody
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.physics.body.removePhysicsBody
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 代表一个16x16x16区块section的物理表示
 * 每个非纯空气的section持有一个静态刚体，使用CompoundShape组合所有方块形状
 *
 * @property worldPos 该section在世界中的坐标
 * @property physicsLevel 所属物理世界
 * @property chunk 对应的区块对象，用于获取方块状态
 */
class PhysicsChunkSection(
    val worldPos: SectionPos,
    override val physicsLevel: PhysicsLevel,
    val chunk: LevelChunk
): PhysicsHost {
    // 缓存section内所有方块的BlockState，避免物理线程中的getBlockState调用
    private val blockStates = mutableMapOf<BlockPos, BlockState>()
    private var collisionShape: CompoundCollisionShape? = null
    private var physicsBody: PhysicsRigidBody? = null
    override val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject> = mutableMapOf()
    var isActive: Boolean = false
        private set

    /**
     * 从缓存的区块中预加载所有方块状态并构建碰撞形状
     * 这个方法只在主线程调用，避免物理线程中的耗时操作
     */
    fun buildCollisionShape(): Boolean {
        val compoundShape = CompoundCollisionShape()
        var hasCollision = false

        // 遍历section内所有方块位置
        for (x in 0..15) {
            for (y in 0..15) {
                for (z in 0..15) {
                    val blockPos = BlockPos(
                        worldPos.minBlockX() + x,
                        worldPos.minBlockY() + y,
                        worldPos.minBlockZ() + z
                    )

                    // 从缓存的区块中获取方块状态，避免直接访问世界
                    val blockState = chunk.getBlockState(blockPos)
                    blockStates[blockPos] = blockState

                    // 跳过空气和没有碰撞体积的方块
                    if (blockState.isAir || blockState.getCollisionShape(physicsLevel.mcLevel, blockPos).isEmpty) {
                        continue
                    }

                    // 获取方块的碰撞形状
                    val blockShape = blockState.getBulletCollisionShape(physicsLevel)

                    // 计算方块在section内的相对位置
                    val relativePos = Vector3f(
                        x + 0.5f - 8f,
                        y + 0.5f - 8f,
                        z + 0.5f - 8f
                    )

                    compoundShape.addChildShape(blockShape, Transform(relativePos, Quaternion.IDENTITY))
                    hasCollision = true
                }
            }
        }

        if (hasCollision) {
            this.collisionShape = compoundShape
            return true
        }

        return false
    }

    /**
     * 创建物理刚体
     */
    fun createPhysicsBody(): Boolean {
        val shape = collisionShape ?: return false

        physicsBody = PhysicsRigidBody(shape, 0f).apply {
            // 设置刚体在世界中的位置（section中心）
            setPhysicsLocation(Vector3f(
                worldPos.minBlockX() + 8f,
                worldPos.minBlockY() + 8f,
                worldPos.minBlockZ() + 8f
            ))
            collisionGroup = CollisionGroups.TERRAIN
            setCollideWithGroups(CollisionGroups.NONE)
            // 存储section信息用于查询
            physicsBody?.owner = this@PhysicsChunkSection
        }

        return true
    }

    /**
     * 将section刚体加入物理世界
     */
    fun activate() {
        if (isActive || physicsBody == null) return
        if (physicsBody!!.isInWorld) {
            isActive = true
            return
        }
        // 使用包含任务队列的方法将任务提交到物理线程，确保在物理线程中执行
        physicsLevel.mcLevel.addPhysicsBody(physicsBody!!)
        isActive = true
    }

    /**
     * 从物理世界移除section刚体
     */
    fun deactivate() {
        if (!isActive || physicsBody == null) return
        if (!physicsBody!!.isInWorld) {
            isActive = false
            return
        }
        // 使用包含任务队列的方法将任务提交到物理线程，确保在物理线程中执行
        physicsLevel.mcLevel.removePhysicsBody(physicsBody!!)
        isActive = false
    }

    /**
     * 获取指定位置的方块状态
     */
    fun getBlockState(blockPos: BlockPos): BlockState? {
        return blockStates[blockPos]
    }

    /**
     * 根据子形状索引获取对应的方块位置
     * 用于在碰撞回调中确定具体碰撞的方块
     */
    fun getBlockPosForChildShape(childIndex: Int): BlockPos? {
        if (childIndex < 0 || childIndex >= 4096) return null

        val x = (childIndex / 256) % 16
        val y = (childIndex / 16) % 16
        val z = childIndex % 16

        return BlockPos(
            worldPos.minBlockX() + x,
            worldPos.minBlockY() + y,
            worldPos.minBlockZ() + z
        )
    }

    /**
     * 处理方块更新（主线程调用）
     * 需要重新构建整个section的形状
     */
    fun onBlockUpdated(blockPos: BlockPos, newState: BlockState): Boolean {
        // 更新缓存
        blockStates[blockPos] = newState

        // 重新构建整个section的形状
        val hadCollision = collisionShape != null
        val hasCollisionNow = buildCollisionShape()

        if (hadCollision != hasCollisionNow) {
            // 碰撞状态发生变化，需要重新创建刚体
            destroyPhysicsBody()
            if (hasCollisionNow) {
                createPhysicsBody()
                if (isActive) {
                    activate()
                }
            }
            return true
        } else if (hasCollisionNow) {
            // 只是形状更新，需要重新创建刚体
            val wasActive = isActive
            destroyPhysicsBody()
            createPhysicsBody()
            if (wasActive) {
                activate()
            }
            return true
        }

        return false
    }

    /**
     * 清理资源
     */
    fun destroy() {
        deactivate()
        destroyPhysicsBody()
        blockStates.clear()
    }

    private fun destroyPhysicsBody() {
        physicsBody?.let {
            physicsLevel.submitImmediateTask(PPhase.PRE) {
                if (it.isInWorld) {
                    physicsLevel.world.removeCollisionObject(it)
                }
            }
            physicsBody = null
        }
        collisionShape = null
        isActive = false
    }

    /**
     * 检查section是否为空（没有碰撞体积）
     */
    fun isEmpty(): Boolean = collisionShape == null
}