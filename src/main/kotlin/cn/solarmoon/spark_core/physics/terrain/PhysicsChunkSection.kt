package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.body.CollisionGroups
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import jme3utilities.math.MyMath
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 代表一个16x16x16区块section的物理表示
 * 每个非纯空气的section持有一个静态刚体，使用CompoundShape组合所有方块形状
 *
 * @property sectionPos 该section在世界中的坐标
 * @property physicsLevel 所属物理世界
 * @property chunk 对应的区块对象，用于获取方块状态
 */
class PhysicsChunkSection(
    val sectionPos: SectionPos,
    override val physicsLevel: PhysicsLevel,
    val chunk: LevelChunk
) : PhysicsHost {
    // 缓存section内所有方块的BlockState，避免物理线程中的getBlockState调用
    private var snapshot: SectionSnapshot? = null
    private var collisionShape: CompoundCollisionShape? = null
    private var physicsBody: PhysicsRigidBody? = null
    override val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject> = mutableMapOf()
    var isActive: Boolean = false
        private set

    // 新增：构建状态管理
    enum class BuildState {
        IDLE,           // 空闲
        BUILDING,       // 构建中
        BUILT,          // 已构建
        FAILED          // 构建失败
    }

    private var buildState = BuildState.IDLE
    private var buildJob: Job? = null

    /**
     * 异步构建碰撞形状
     * 在专用线程池中执行，避免阻塞主线程
     */
    private fun buildCollisionShapeAsync(scope: CoroutineScope): Deferred<Boolean> = scope.async {
        if (buildState == BuildState.BUILDING) {
            return@async false
        }

        buildState = BuildState.BUILDING
        try {
            val result = buildCollisionShape()
            buildState = if (result) BuildState.BUILT else BuildState.IDLE
            result
        } catch (e: Exception) {
            buildState = BuildState.FAILED
            SparkCore.LOGGER.error("构建section ${sectionPos} 碰撞形状失败", e)
            false
        }
    }

    /**
     * 从缓存的区块中预加载所有方块状态并构建碰撞形状
     *
     * 该方法会：
     * 1. 遍历section缓存的所有方块位置
     * 2. 获取每个方块的碰撞形状
     * 3. 处理复合形状，将其拆分为基本形状并应用变换
     * 4. 将所有非空气方块的形状组合到最终的复合形状中
     *
     * @return Boolean 如果section包含任何碰撞体积则返回true，否则返回false
     */
    private fun buildCollisionShape(): Boolean {
        val compoundShape = CompoundCollisionShape()
        var hasCollision = false
        val snapshot = snapshot ?: return false
        // 遍历section内所有方块位置
        for (block in snapshot.shapes) {
            val origin = BlockPos(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ())
            val blockPos = block.key.subtract(origin)
            val blockInfo = block.value
            // 获取方块的碰撞形状，其他物理信息在其他刚体发生碰撞处理碰撞对时自行调用使用
            val blockShape = blockInfo.state.getBulletCollisionShape(physicsLevel) ?: continue
            // 计算方块在section内的相对位置
            val relativePos = Vector3f(
                blockPos.x + 0.5f - 8f,
                blockPos.y + 0.5f - 8f,
                blockPos.z + 0.5f - 8f
            )

            // 处理复合形状：如果是复合形状，需要拆分为基本形状
            if (blockShape is CompoundCollisionShape) {
                // 遍历复合形状的所有子形状
                val children = blockShape.listChildren()
                for (childShape in children) {
                    val shape = childShape.shape
                    val childTransform = childShape.copyTransform(null)

                    // 合并变换：方块位置 + 子形状相对位置
                    val combinedTransform =
                        MyMath.combine(Transform(relativePos, Quaternion.IDENTITY), childTransform, null)

                    // 添加子形状到最终的复合形状
                    compoundShape.addChildShape(shape, combinedTransform)
                }
            } else {
                // 对于非复合形状，直接添加
                compoundShape.addChildShape(blockShape, Transform(relativePos, Quaternion.IDENTITY))
            }

            hasCollision = true
        }

        if (hasCollision) {
            this.collisionShape = compoundShape
            return true
        }

        return false
    }

    /**
     * 开始异步构建过程
     */
    fun startAsyncBuild(manager: PhysicsChunkManager) {
        // 取消之前的构建任务
        buildJob?.cancel()
        // 缓存section内所有方块信息
        snapshot = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
        buildJob = manager.terrainBuilderScope.launch {
            val buildResult = buildCollisionShapeAsync(this).await()

            if (buildResult) {
                // 形状构建完成，在物理线程中创建刚体
                physicsLevel.submitImmediateTask {
                    createPhysicsBody()
                }
            }
        }
    }

    fun startAsyncUpdate(manager: PhysicsChunkManager) {
        // 取消之前的构建任务
        buildJob?.cancel()
        // 缓存section内所有方块信息
        snapshot = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
        buildJob = manager.terrainBuilderScope.launch {
            val wasActive = isActive
            val hadBody = physicsBody != null
            val hasCollisionNow = buildCollisionShapeAsync(this).await()
            if (hasCollisionNow) {
                if (hadBody) {
                    // 重用现有刚体，在物理线程中更新形状
                    physicsBody?.let { body ->
                        collisionShape?.let { newShape ->
                            physicsLevel.submitImmediateTask {
                                body.collisionShape = newShape
                            }
                        }
                    }
                } else {
                    // 没有现有刚体，在物理线程中创建新的
                    physicsLevel.submitImmediateTask {
                        createPhysicsBody()
                        if (wasActive) {
                            activate()
                        }
                    }
                }
            } else {
                // 新的形状为空，在物理线程中销毁刚体
                if (hadBody) {
                    physicsLevel.submitImmediateTask {
                        destroyPhysicsBody()
                    }
                }
            }
        }
    }

    /**
     * 创建物理刚体
     */
    private fun createPhysicsBody(): Boolean {
        val shape = collisionShape ?: return false

        physicsBody = createPhysicsBody(shape, 0f, "section_$sectionPos")
        physicsBody!!.setPhysicsLocation(
            Vector3f(
                sectionPos.minBlockX() + 8f,
                sectionPos.minBlockY() + 8f,
                sectionPos.minBlockZ() + 8f
            )
        )
        physicsBody!!.collisionGroup = CollisionGroups.TERRAIN
        physicsBody!!.setCollideWithGroups(CollisionGroups.NONE)
        return true
    }

    /**
     * 取消正在进行的构建任务
     */
    fun cancelBuild() {
        buildJob?.cancel()
        buildJob = null
        if (buildState == BuildState.BUILDING) {
            buildState = BuildState.IDLE
        }
    }

    /**
     * 检查是否正在构建中
     */
    fun isBuilding(): Boolean = buildState == BuildState.BUILDING

    /**
     * 检查是否已构建完成
     */
    fun isBuilt(): Boolean = buildState == BuildState.BUILT

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
        addPhysicsBody(physicsBody!!)
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
        removePhysicsBodyFromWorld(physicsBody!!)
        isActive = false
    }

    /**
     * 获取指定世界位置的方块状态
     */
    fun getBlockState(blockPos: BlockPos): BlockState? {
        if (isEmpty()) return null
        return getBlockSnapshot(blockPos)?.state
    }

    /**
     * 获取指定世界位置的方块信息
     */
    fun getBlockSnapshot(blockPos: BlockPos): SectionSnapshot.BlockSnapshot? {
        if (isEmpty()) return null
        return snapshot!!.shapes[blockPos]
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
            sectionPos.minBlockX() + x,
            sectionPos.minBlockY() + y,
            sectionPos.minBlockZ() + z
        )
    }

    /**
     * 清理资源
     */
    fun destroy() {
        cancelBuild()
        deactivate()
        destroyPhysicsBody()
        snapshot?.shapes?.clear()
        snapshot = null
    }

    private fun destroyPhysicsBody() {
        removePhysicsBody(physicsBody)
        collisionShape = null
        isActive = false
    }

    /**
     * 检查section是否为空（没有碰撞体积）
     */
    fun isEmpty(): Boolean = collisionShape == null
}