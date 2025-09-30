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
import jme3utilities.math.MyMath
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
     *
     * 该方法会：
     * 1. 遍历section内所有方块位置
     * 2. 获取每个方块的碰撞形状
     * 3. 处理复合形状，将其拆分为基本形状并应用变换
     * 4. 将所有非空气方块的形状组合到最终的复合形状中
     *
     * @return Boolean 如果section包含任何碰撞体积则返回true，否则返回false
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
                    blockStates.clear()
                    val blockState = chunk.getBlockState(blockPos)
                    // 跳过空气和没有碰撞体积的方块
                    if (blockState.isAir || blockState.getCollisionShape(physicsLevel.mcLevel, blockPos).isEmpty) {
                        continue
                    }
                    blockStates[blockPos] = blockState

                    // 获取方块的碰撞形状
                    val blockShape = blockState.getBulletCollisionShape(physicsLevel)

                    // 计算方块在section内的相对位置
                    val relativePos = Vector3f(
                        x + 0.5f - 8f,
                        y + 0.5f - 8f,
                        z + 0.5f - 8f
                    )

                    // 处理复合形状：如果是复合形状，需要拆分为基本形状
                    if (blockShape is CompoundCollisionShape) {
                        // 遍历复合形状的所有子形状
                        val children = blockShape.listChildren()
                        for (childShape in children) {
                            val shape = childShape.shape
                            val childTransform = childShape.copyTransform(null)

                            // 合并变换：方块位置 + 子形状相对位置
                            val combinedTransform = MyMath.combine(Transform(relativePos, Quaternion.IDENTITY), childTransform, null)

                            // 添加子形状到最终的复合形状
                            compoundShape.addChildShape(shape, combinedTransform)
                        }
                    } else {
                        // 对于非复合形状，直接添加
                        compoundShape.addChildShape(blockShape, Transform(relativePos, Quaternion.IDENTITY))
                    }

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
        }
        physicsBody!!.owner = this
        return true
    }

    /**
     * 更新物理刚体的碰撞形状
     *
     * 该方法会：
     * 1. 重新构建section的碰撞形状
     * 2. 根据新的碰撞形状决定是否需要创建、更新或销毁刚体
     * 3. 重用现有刚体以避免重新加入物理世界的开销
     *
     * @return Boolean 如果刚体状态发生变化（创建、销毁或形状更新）则返回true，否则返回false
     *
     * 处理逻辑：
     * - 如果section现在有碰撞体积且之前有刚体：更新现有刚体的碰撞形状
     * - 如果section现在有碰撞体积且之前没有刚体：创建新刚体
     * - 如果section现在没有碰撞体积且之前有刚体：销毁刚体
     * - 如果section现在没有碰撞体积且之前没有刚体：无操作
     */
    fun updatePhysicsBody(): Boolean {
        val wasActive = isActive
        val hadBody = physicsBody != null

        // 重新构建碰撞形状
        val hasCollisionNow = buildCollisionShape()
        var changed = false

        if (hasCollisionNow) {
            if (hadBody) {
                // 重用现有刚体，只更新形状
                physicsBody?.let { body ->
                    collisionShape?.let { newShape ->
                        physicsLevel.submitImmediateTask {
                            body.collisionShape = newShape
                        }
                    }
                }
                changed = true
            } else {
                // 没有现有刚体，创建新的
                createPhysicsBody()
                if (wasActive) {
                    activate()
                }
                changed = true
            }
        } else {
            // 新的形状为空，销毁刚体
            if (hadBody) {
                destroyPhysicsBody()
                changed = true
            }
        }
        return changed
    }

    /**
     * 将section刚体加入物理世界
     */
    fun activate() {
        if (isActive || physicsBody == null) {
            isActive = true
            return
        }
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
        if (!isActive || physicsBody == null) {
            isActive = false
            return
        }
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