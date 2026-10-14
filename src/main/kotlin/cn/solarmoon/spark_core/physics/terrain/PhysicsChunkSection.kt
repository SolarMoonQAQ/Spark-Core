package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.PhysicsHost
import cn.solarmoon.spark_core.physics.body.CollisionGroups
import cn.solarmoon.spark_core.physics.body.addPhysicsBody
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.physics.body.removePhysicsBody
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.BlockCollisionUtil
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.simsilica.mathd.Vec3d
import jme3utilities.math.MyMath
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import kotlin.math.floor

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
    val blockMerger = BlockMerger(physicsLevel)
    // 缓存section内所有方块的BlockState，避免物理线程中的getBlockState调用
    private var snapshotForBuild: SectionSnapshot? = null
    @Volatile
    private var snapshotForCollision: SectionSnapshot? = null

    /**
     * 流体索引的**独立发布通道**（物理线程读取入口见 [getFluidIndex]）。
     *
     * 为什么不由 `snapshotForCollision` 携带：后者只在 `buildCollisionShape()` 成功后
     * 由 `createPhysicsBody()` 发布，而纯流体 section 永远 build 失败（无刚体），
     * 会导致「水越深、越纯，浮力越查不到」——船完全入水时采样点所在的整节全是水、
     * 一块固体都没有，恰好是最需要这份数据的场景。
     *
     * 也不能直接读 `snapshotForBuild`：它没有 `@Volatile`，物理线程读它既无可见性保证，
     * 也会与 `startAsyncUpdate()` 的重建竞争。
     *
     * 主线程写（[publishFluidIndex]）、物理线程读；`SectionFluidIndex` 实例不可变，
     * 故单个 volatile 写即可安全发布。
     *
     * 额外好处：发布点在 `prepareSnapshot()`，而它由 `PhysicsChunk.load()` 对**所有**
     * sectionY 调用 —— 因此流体索引在区块加载后、且在任何激活之前即可用，
     * 不需要该 section 被激活或建出刚体。
     */
    @Volatile
    private var fluidIndexRef: SectionFluidIndex? = null
    @Volatile
    private var lastSlipEpoch: Int = Int.MIN_VALUE
    @Volatile
    private var collisionShape: CompoundCollisionShape? = null
    @Volatile
    var physicsBody: PhysicsRigidBody? = null  // 公开以支持 Java 端 DDA 补充命中条目时获取刚体引用
    override val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject> = mutableMapOf()
    @Volatile
    var isActive: Boolean = false
        private set

    // 新增：构建状态管理
    enum class BuildState {
        IDLE,           // 空闲
        BUILDING,       // 构建中
        BUILT,          // 已构建
        FAILED          // 构建失败
    }

    @Volatile
    private var buildState = BuildState.IDLE
    private var buildJob: Job? = null

    /**
     * 懒构建待激活标记：section 尚未构建完成时被 [activate] 请求过激活，
     * 构建完成后自动加入物理世界。激活需求取消时（deactivate/cancelPendingActivation）清除。
     */
    @Volatile
    private var pendingActivation = false

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
     * 使用洪水填充算法合并相同形状的方块
     */
    private fun buildCollisionShape(): Boolean {
        val compoundShape = CompoundCollisionShape()
        var hasCollision = false
        val snapshot = snapshotForBuild
        // 守卫用 hasSolid 而非 EMPTY：纯流体 section 有流体通道内容，不是 EMPTY，
        // 但它没有任何固体方块，不应进入（必然空手而归的）刚体构建流程。
        if (snapshot == null || snapshot.isEmpty() || !snapshot.hasSolid) {
            collisionShape = null
            return false
        }

        // 按Y层处理
        for (levelY in 0 until 16) {
            // 准备当前层的方块状态数组
            val layerBlocks = Array(16) { Array<BlockState?>(16) { null } }

            for (x in 0 until 16) {
                for (z in 0 until 16) {
                    val blockSnapshot = snapshot.getBlockSnapshot(x, levelY, z)
                    if (blockSnapshot != null) {
                        layerBlocks[x][z] = blockSnapshot.state
                    }
                }
            }

            // 合并当前层的方块
            val mergedRects = blockMerger.mergeLayer(levelY, layerBlocks)

            // 添加合并后的形状
            for (rect in mergedRects) {
                val collisionShape = rect.toCollisionShape()
                val centerPos = rect.getCenter(levelY)
                compoundShape.addChildShape(collisionShape, centerPos)
                hasCollision = true
            }

            // 处理无法合并的复杂形状
            for (x in 0 until 16) {
                for (z in 0 until 16) {
                    val blockSnapshot = snapshot.getBlockSnapshot(x, levelY, z)
                    if (blockSnapshot != null) {
                        // 如果是复杂形状且没有被合并过
                        if (!physicsLevel.blockShapeManager.isMergeableShape(blockSnapshot.state)) {
                            val shape = blockSnapshot.state.getBulletCollisionShape(physicsLevel)
                            addComplexShape(compoundShape, x, levelY, z, shape)
                            hasCollision = true
                        }
                    }
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
     * 添加复杂形状（保持原有逻辑）
     */
    private fun addComplexShape(
        compoundShape: CompoundCollisionShape,
        x: Int, y: Int, z: Int,
        shapeToAdd: CollisionShape
    ) {
        val relativePos = Vector3f(
            x + 0.5f - 8f,
            y + 0.5f - 8f,
            z + 0.5f - 8f
        )

        // 处理复合形状：如果是复合形状，需要拆分为基本形状
        if (shapeToAdd is CompoundCollisionShape) {
            // 遍历复合形状的所有子形状
            val children = shapeToAdd.listChildren()
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
            compoundShape.addChildShape(shapeToAdd, Transform(relativePos, Quaternion.IDENTITY))
        }
    }

    /**
     * 主线程调用：预填充构建快照（廉价），不排队构建作业。
     * 供懒构建使用（PhysicsChunk.load 时调用），构建作业推迟到首次激活时触发。
     */
    fun prepareSnapshot() {
        if (snapshotForBuild == null) {
            snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
            publishFluidIndex()
        }
    }

    /**
     * 主线程发布流体索引引用。
     *
     * 必须在每次 `snapshotForBuild` 被重新赋值后调用（[prepareSnapshot] / [startAsyncUpdate]）。
     */
    private fun publishFluidIndex() {
        fluidIndexRef = snapshotForBuild?.fluidIndex
    }

    /**
     * 获取本 section 的流体索引（物理线程调用）。
     *
     * **不得**加 `isEmpty()` / `hasSolid` 守卫：纯流体 section 的 `collisionShape` 恒为 null，
     * `isEmpty()` 恒为 true，顺手加守卫就会把整个功能挡掉。
     */
    fun getFluidIndex(): SectionFluidIndex? = fluidIndexRef

    /**
     * 开始异步构建过程(主线程调用)
     */
    fun startAsyncBuild(manager: PhysicsChunkManager) {
        // 取消之前的构建任务
        buildJob?.cancel()
        // 缓存section内所有方块信息（懒构建路径下已由 prepareSnapshot 预填充，此处兜底）
        prepareSnapshot()
        buildJob = manager.terrainBuilderScope.launch {
            val buildResult = buildCollisionShapeAsync(this).await()

            if (buildResult) {
                // 纯Java部分在构建线程完成（含内部replaceCollisionSnapshot），刚体不加入世界，等待激活
                createPhysicsBody()
                // 懒构建：若构建期间该 section 已被请求激活，构建完成后自动加入物理世界
                if (pendingActivation) {
                    pendingActivation = false
                    activate()
                }
            } else {
                // 构建失败或为空形状：清除待激活标记，避免后续误激活
                pendingActivation = false
            }
        }
    }

    /**
     * 开始异步构建过程(主线程调用)
     */
    fun startAsyncUpdate(manager: PhysicsChunkManager) {
        // 取消之前的构建任务
        buildJob?.cancel()
        buildJob = null
        // 缓存section内所有方块信息
        val snapshot = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
        snapshotForBuild = snapshot
        // 流体索引在此同步发布（主线程 volatile 写），与后续刚体构建成功与否无关
        publishFluidIndex()

        // 流体-only 快路径：无固体 ⇒ 不需要 4096 格 getCollisionShape + 全量 BlockMerger + 快照克隆。
        // 流水每 tick 都会走 onBlockStateChange → startAsyncUpdate，而固体部分一个格子都没变，
        // 走完整重建流程纯属浪费（见 docs/地形方块变更热路径优化建议.md §2.3）。
        if (!snapshot.hasSolid) {
            if (physicsBody != null) {
                physicsLevel.submitImmediateTask { destroyPhysicsBody() }
            }
            collisionShape = null
            buildState = BuildState.IDLE
            return
        }

        buildJob = manager.terrainBuilderScope.launch {
            val wasActive = isActive
            val hadBody = physicsBody != null
            val hasCollisionNow = buildCollisionShapeAsync(this).await()
            if (hasCollisionNow) {
                if (hadBody) {
                    // 重用现有刚体：纯Java部分在构建线程完成，仅shape替换交给物理线程
                    replaceCollisionSnapshot(snapshotForBuild)
                    val newShape = collisionShape!!
                    physicsLevel.submitImmediateTask(PPhase.PRE) {
                        physicsBody!!.collisionShape = newShape
                    }
                } else {
                    // 构建新刚体：纯Java部分在构建线程完成（含内部replaceCollisionSnapshot），仅加入世界交给物理线程
                    createPhysicsBody()
                    if (wasActive) {
                        val body = physicsBody!!
                        physicsLevel.submitImmediateTask {
                            physicsLevel.world.addCollisionObject(body)
                        }
                    }
                }
            } else {
                // 形状为空需销毁刚体：含removeCollisionObject，仍需在物理线程执行
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
        replaceCollisionSnapshot(snapshotForBuild)
        physicsBody = PhysicsRigidBody(shape, 0f).apply {
            name = "section_$sectionPos"
            owner = this@PhysicsChunkSection
        }
        // section 中心是绝对世界坐标：|x| 超过 2^24 后 `int + 8f` 已无法精确表示，
        // 因此用 Dp 写入，避免地形整体在大坐标处被 float 量化（决策 D4）
        physicsBody!!.setPhysicsLocationDp(
            Vec3d(
                sectionPos.minBlockX() + 8.0,
                sectionPos.minBlockY() + 8.0,
                sectionPos.minBlockZ() + 8.0
            )
        )
        physicsBody!!.collisionGroup = CollisionGroups.TERRAIN
        physicsBody!!.setCollideWithGroups(CollisionGroups.NONE)
//        physicsBody!!.shouldShowDebugBoxWhenNonColldeWith = true
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
     *
     * 懒构建：若刚体尚未构建完成（physicsBody == null），标记待激活并触发异步构建，
     * 构建完成后自动加入物理世界（见 [startAsyncBuild]）。
     */
    fun activate() {
        if (isActive) return
        val body = physicsBody
        if (body != null) {
            refreshSlipIfNeeded(physicsLevel.terrainManager.currentWeatherEpoch())
            if (body.isInWorld) {
                isActive = true
                return
            }
            // 使用包含任务队列的方法将任务提交到物理线程，确保在物理线程中执行
            body.owner = this
            physicsLevel.mcLevel.addPhysicsBody(body)
            isActive = true
            return
        }
        // 纯流体 section 永远 build 不出刚体（buildCollisionShape 在 !hasSolid 时早退）。
        // 不必为它排队构建作业 —— 海洋里每个 chunk 会有约 8 个这样的 section。
        if (snapshotForBuild?.hasSolid == false) return

        // 尚未构建：标记待激活并触发懒构建（构建完成后自动激活）
        pendingActivation = true
        if (buildState == BuildState.IDLE) {
            startAsyncBuild(physicsLevel.terrainManager)
        }
    }

    /**
     * 取消懒构建的待激活标记（section 已不在任何激活范围时调用）。
     * 不影响已开始的构建作业；构建完成后因标记已清除而不会自动加入物理世界。
     */
    fun cancelPendingActivation() {
        pendingActivation = false
    }

    /**
     * 从物理世界移除section刚体
     */
    fun deactivate() {
        if (!isActive || physicsBody == null) {
            // 尚未构建完成：同时清除待激活标记，避免构建完成后自动激活
            pendingActivation = false
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
     * 获取指定世界位置的方块状态(物理线程调用)
     */
    fun getBlockState(worldPos: BlockPos): BlockState? {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return null
        return getBlockSnapshot(worldPos)?.state
    }

    /**
     * 获取指定世界位置的方块信息(物理线程调用)
     */
    fun getBlockSnapshot(worldPos: BlockPos): SectionSnapshot.BlockSnapshot? {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return null
        return snapshotForCollision!!.getBlockSnapshotFromWorld(worldPos)
    }

    /**
     * 根据碰撞点获取方块位置
     */
    fun getBlockPosFromContactPoint(worldContactPoint: Vector3f, normal: Vector3f, distance: Float): BlockPos {
        val pos = worldContactPoint.add(normal.mult(distance))
        return BlockPos(
            floor(pos.x.toDouble()).toInt(),
            floor(pos.y.toDouble()).toInt(),
            floor(pos.z.toDouble()).toInt()
        )
    }

    /**
     * 标记指定位置的方块为已移除(物理线程调用)
     */
    fun markRemoved(worldPos: BlockPos) {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return
        if (isRemoved(worldPos)) return
        val pos = snapshotForCollision!!.getRelativePos(worldPos)
        val index = SectionSnapshot.getIndexFromRelativePos(pos.x, pos.y, pos.z)
        snapshotForCollision!!.blockSnapshots[index] = null
    }

    /**
     * 判断指定位置的方块是否已被标记移除(物理线程调用)
     */
    fun isRemoved(worldPos: BlockPos): Boolean {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return true
        return snapshotForCollision!!.getBlockSnapshotFromWorld(worldPos) == null
    }

    /**
     * 清理资源
     */
    fun destroy() {
        cancelBuild()
        pendingActivation = false
        destroyPhysicsBody()
        snapshotForBuild = null
        snapshotForCollision = null
        fluidIndexRef = null
        lastSlipEpoch = Int.MIN_VALUE
    }

    private fun destroyPhysicsBody() {
        physicsBody?.let { if (it.isInWorld) physicsLevel.mcLevel.removePhysicsBody(it) }
        collisionShape = null
        isActive = false
    }

    /**
     * 检查section是否为空（没有碰撞体积）
     */
    fun isEmpty(): Boolean = collisionShape == null

    /**
     * 判断该 section 是否不包含任何有碰撞体积的方块（纯空气、纯流体或仅无碰撞方块）。
     *
     * 基于构建快照判断：快照由 [startAsyncBuild] / [startAsyncUpdate] 在主线程同步写入，
     * 因此在构建完成前也能可靠区分 "无固体（无支撑需求）" 与 "尚未构建完成"。
     *
     * 判据必须是 `hasSolid` 而不是 `isEmpty()`：纯水 section 有流体通道内容，不再是 EMPTY，
     * 但它永远不会 build（`buildCollisionShape()` 早退），若用 `isEmpty()` 判定会让
     * `isTerrainReady()` 恒为 false ⇒ 载具被判为"地形未就绪"而卡住。
     */
    fun hasNoCollisionBlocks(): Boolean {
        val snapshot = snapshotForBuild ?: return true
        return snapshot.hasSolid != true
    }

    /**
     * 仅刷新 snapshot 中的 slip，不触发 shape 重建和刚体重建
     */
    fun refreshSlipIfNeeded(epoch: Int) {
        if (lastSlipEpoch == epoch) return
        val snapshot = snapshotForCollision
        // 纯流体 section 的 blockSnapshots 是全 null 的共享只读数组，
        // 下面的循环只会空转 4096 次，直接跳过。
        if (snapshot == null || snapshot.isEmpty() || !snapshot.hasSolid) {
            lastSlipEpoch = epoch
            return
        }

        val snapshots = snapshot.blockSnapshots
        for (index in snapshots.indices) {
            val blockSnapshot = snapshots[index] ?: continue
            val relativePos = SectionSnapshot.getRelativePosFromIndex(index)
            val worldPos = snapshot.getWorldPos(relativePos)
            val newSlip = BlockCollisionUtil.getSlip(chunk, blockSnapshot.state, worldPos)
            if (newSlip != blockSnapshot.slip) {
                snapshots[index] = blockSnapshot.withSlip(newSlip)
            }
        }
        lastSlipEpoch = epoch
    }

    private fun replaceCollisionSnapshot(source: SectionSnapshot?) {
        snapshotForCollision = source?.copy()
        lastSlipEpoch = Int.MIN_VALUE
    }
}
