package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.api.*
import cn.solarmoon.spark_core.physics.body.CollisionGroups
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.toVec3
import com.jme3.bullet.collision.PhysicsCollisionEvent
import com.jme3.bullet.collision.PhysicsCollisionListener
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.objects.PhysicsGhostObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import io.netty.util.internal.ConcurrentSet
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.network.PacketDistributor
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 管理所有物理区块的加载、卸载和激活
 * 负责根据载具位置动态管理物理区块的激活状态
 * 所有耗时操作都在主线程执行，避免物理线程阻塞
 *
 * @property physicsLevel 所属物理世界
 */
class PhysicsChunkManager(
    private val physicsLevel: PhysicsLevel
): PhysicsCollisionListener {
    private enum class WeatherPhase {
        DRY, RAINING
    }

    private val activator = PhysicsGhostObject(SphereCollisionShape(2f))

    // 已加载的物理区块（包含构建状态）
    private val loadedChunks = mutableMapOf<ChunkPos, PhysicsChunk>()

    // Minecraft已加载的区块（不构建物理表示）
    private val mcLoadedChunks = mutableMapOf<ChunkPos, LevelChunk>()

    // 脏section管理
    private val dirtySections = ConcurrentSet<SectionPos>()

    // 地形构建线程池
    private val terrainBuilderExecutor = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1).coerceAtMost(2)
    ) { r -> Thread(r, "TerrainShapeBuilder-${physicsLevel.name}") }.asCoroutineDispatcher()

    val terrainBuilderScope = CoroutineScope(
        terrainBuilderExecutor + SupervisorJob() +
                CoroutineExceptionHandler { _, exception ->
                    SparkCore.LOGGER.error("地形构建线程异常", exception)
                }
    )

    // 配置参数
    private val buildRadius = 2 // 构建半径（区块数）
    private val activationRadius = 2 // 激活半径（方块数）
    @Volatile
    private var weatherEpoch = 0
    private var lastWeatherPhase = currentWeatherPhase()

    // ===== 区块实体方块高程索引（Phase 1） =====

    /** 区块高程索引，内存常驻，供物理线程查询未加载区块的地形信息。 */
    val chunkHeightIndex = ChunkHeightIndex()

    // ===== 预约调度系统（Phase 2.7，基于 MC Ticket） =====

    /**
     * 每个 chunk 的所有活跃调度请求的过期 tick 列表。
     * 用于 cancelAllSchedules 时正确重算最晚过期时间。
     * 多个投射物可同时调度同一 chunk，各自独立追踪。
     *
     * key: ChunkPos，value: 该 chunk 所有活跃请求的 expireTick
     */
    private val chunkActiveRequests = ConcurrentHashMap<ChunkPos, MutableList<Int>>()

    /**
     * 每个 chunk 的最晚过期 tick（合并后的值）。
     * 用于每 tick 扫表判断哪些 chunk 需要自动释放。
     * 多次调度同一 chunk 时取最晚值。
     */
    private val chunkExpireTicks = ConcurrentHashMap<ChunkPos, Int>()

    // ===== 预约调度：加载任务去重 key 前缀 =====
    private val TERRAIN_LOAD_KEY_PREFIX = "terrain_load_"

    // 性能统计
    private val totalSections: Int
        get() = loadedChunks.values.sumOf { it.getTotalSectionCount() }
    private val activeSections: Int
        get() = loadedChunks.values.sumOf { it.getActiveSectionCount() }

    init {
        activator.addCollideWithGroup(CollisionGroups.PHYSICS_BODY)
    }

    /**
     * 当前天气版本号，用于 section 的湿滑缓存刷新去重
     */
    fun currentWeatherEpoch(): Int = weatherEpoch

    /**
     * 在主线程每 tick 调用：
     * - 检测天气阶段（晴/雨）是否变化
     * - 若变化，仅刷新当前已激活 section 的湿滑系数
     */
    fun updateWeatherSlipIfNeeded() {
        val newPhase = currentWeatherPhase()
        if (newPhase == lastWeatherPhase) return
        lastWeatherPhase = newPhase
        weatherEpoch++
        refreshActiveSectionSlip()
    }

    private fun currentWeatherPhase(): WeatherPhase {
        return if (physicsLevel.mcLevel.isRaining) WeatherPhase.RAINING else WeatherPhase.DRY
    }

    /**
     * 仅刷新活跃 section，避免走重型重建路径
     */
    private fun refreshActiveSectionSlip() {
        val minSection = physicsLevel.mcLevel.minSection
        val maxSection = physicsLevel.mcLevel.maxSection
        loadedChunks.values.forEach { chunk ->
            for (sectionY in minSection until maxSection) {
                val section = chunk.getSection(sectionY) ?: continue
                if (section.isActive) {
                    section.refreshSlipIfNeeded(weatherEpoch)
                }
            }
        }
    }

    /**
     * 卸载指定区块的物理表示
     */
    fun unloadPhysicsChunk(chunkPos: ChunkPos) {
        val chunk = loadedChunks[chunkPos] ?: return

        // 从脏section集合中移除该区块的所有section
        dirtySections.removeAll { it == chunkPos }

        chunk.unload()
        loadedChunks.remove(chunkPos)
    }

    /**
     * 根据刚体位置更新构建范围
     */
    fun updateBuild(boundingBoxes: List<AABB>) {

        // 1. 收集需要构建的区块
        val chunksToBuild = collectChunksToBuild(boundingBoxes)

        // 2. 开始构建新区块
        startBuildingChunks(chunksToBuild)

    }

    /**
     * 收集所有需要构建的区块
     */
    private fun collectChunksToBuild(boundingBoxes: List<AABB>): Set<ChunkPos> {
        val chunksToBuild = mutableSetOf<ChunkPos>()

        boundingBoxes.forEach { aabb ->
            // 忽视超出可建造范围的AABB
            val minY = SectionPos.blockToSectionCoord(aabb.minY.toInt())
            val maxY = SectionPos.blockToSectionCoord(aabb.maxY.toInt())
            if (minY > physicsLevel.mcLevel.maxSection + 1 || maxY < physicsLevel.mcLevel.minSection - 1) return@forEach
            // 计算构建范围的区块坐标
            val minBuildX = SectionPos.blockToSectionCoord(aabb.minX.toInt()) - buildRadius
            val maxBuildX = SectionPos.blockToSectionCoord(aabb.maxX.toInt()) + buildRadius
            val minBuildZ = SectionPos.blockToSectionCoord(aabb.minZ.toInt()) - buildRadius
            val maxBuildZ = SectionPos.blockToSectionCoord(aabb.maxZ.toInt()) + buildRadius

            // 收集范围内的所有区块
            for (chunkX in minBuildX..maxBuildX) {
                for (chunkZ in minBuildZ..maxBuildZ) {
                    val chunkPos = ChunkPos(chunkX, chunkZ)
                    if (chunkPos in mcLoadedChunks) {
                        chunksToBuild.add(chunkPos)
                    }
                }
            }
        }

        return chunksToBuild
    }

    /**
     * 开始构建新区块
     */
    private fun startBuildingChunks(chunksToBuild: Set<ChunkPos>) {
        chunksToBuild.forEach { chunkPos ->
            // 如果区块未构建，开始构建
            if (chunkPos !in loadedChunks) {
                val mcChunk = mcLoadedChunks[chunkPos] ?: return@forEach
                val chunk = PhysicsChunk(chunkPos, physicsLevel, mcChunk)
                chunk.load()
                loadedChunks[chunkPos] = chunk
            }
        }
    }

    /**
     * 在物理线程中获取指定世界位置的方块快照
     * 用于碰撞处理等物理计算
     *
     * @param worldPos 世界坐标
     * @return 方块快照，如果位置无效或没有碰撞体积则返回null
     */
    fun getBlockSnapshotAt(worldPos: BlockPos): SectionSnapshot.BlockSnapshot? {
        val section = getSectionForBlockPos(worldPos) ?: return null
        return section.getBlockSnapshot(worldPos)
    }

    /**
     * 根据BoundingBox列表更新区块激活状态
     * 统一激活所有BoundingBox范围内的section，停用范围外的section
     */
    fun updateActivation(boundingBoxes: List<AABB>) {
        if (boundingBoxes.isEmpty()) {
            // 如果没有BoundingBox，停用所有section
            loadedChunks.values.forEach { it.deactivateAll() }
            return
        }

        // 使用Map记录每个区块需要激活的Y范围
        val activationMap = mutableMapOf<ChunkPos, MutableSet<IntRange>>()

        // 收集所有需要激活的区块和section范围
        boundingBoxes.forEach { aabb ->
            // 忽视超出可建造范围的AABB
            val minY = SectionPos.blockToSectionCoord((aabb.minY - activationRadius).toInt())
            val maxY = SectionPos.blockToSectionCoord((aabb.maxY + activationRadius).toInt())
            if (minY > physicsLevel.mcLevel.maxSection || maxY < physicsLevel.mcLevel.minSection) return@forEach
            // 计算BoundingBox覆盖的区块和section范围
            val minChunkX = SectionPos.blockToSectionCoord((aabb.minX - activationRadius).toInt())
            val maxChunkX = SectionPos.blockToSectionCoord((aabb.maxX + activationRadius).toInt())
            val minChunkZ = SectionPos.blockToSectionCoord((aabb.minZ - activationRadius).toInt())
            val maxChunkZ = SectionPos.blockToSectionCoord((aabb.maxZ + activationRadius).toInt())

            // 计算BoundingBox覆盖的section Y范围
            val minSectionY = SectionPos.blockToSectionCoord((aabb.minY - activationRadius).toInt())
            val maxSectionY = SectionPos.blockToSectionCoord((aabb.maxY + activationRadius).toInt())
            val sectionRange = minSectionY..maxSectionY

            // 为每个受影响的区块添加激活范围
            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    val chunkPos = ChunkPos(chunkX, chunkZ)
                    activationMap.getOrPut(chunkPos) { mutableSetOf() }.add(sectionRange)
                }
            }
        }

        loadedChunks.values.forEach { chunk ->
            val chunkPos = chunk.chunkPos
            val activationRanges = activationMap[chunkPos]

            if (!activationRanges.isNullOrEmpty()) {
                // 合并该区块的所有激活范围
                val mergedRanges = mergeRanges(activationRanges)
                // 激活合并后的范围
                chunk.activateSectionsInRanges(mergedRanges)
            } else {
                // 停用不在激活范围内的区块
                chunk.deactivateAll()
            }
        }
    }

    /**
     * 合并重叠的IntRange
     */
    private fun mergeRanges(ranges: Set<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()

        val sortedRanges = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var currentRange = sortedRanges.first()

        for (range in sortedRanges.drop(1)) {
            if (range.first <= currentRange.last + 1) {
                // 范围重叠或相邻，合并
                currentRange = currentRange.first..maxOf(currentRange.last, range.last)
            } else {
                // 不重叠，保存当前范围并开始新的
                merged.add(currentRange)
                currentRange = range
            }
        }
        merged.add(currentRange)

        return merged
    }

    /**
     * 向后兼容的重载方法，接受单个焦点位置
     */
    fun updateActivation(focusPos: Vector3f) {
        // 创建以焦点位置为中心的BoundingBox
        val halfSize = 16.0 // 区块半径转换为方块数
        val aabb = AABB(
            focusPos.x - halfSize, focusPos.y - halfSize, focusPos.z - halfSize,
            focusPos.x + halfSize, focusPos.y + halfSize, focusPos.z + halfSize
        )
        updateActivation(listOf(aabb))
    }

    /**
     * 获取指定位置的物理section
     */
    fun getSectionForBlockPos(blockPos: BlockPos): PhysicsChunkSection? {
        val chunkPos = ChunkPos(blockPos)
        return loadedChunks[chunkPos]?.getSectionForBlockPos(blockPos)
    }

    /**
     * 处理区块加载事件（主线程调用）
     *
     * 除了存入 mcLoadedChunks，还计算并写入区块高程索引。
     */
    fun onChunkLoaded(chunkPos: ChunkPos, chunk: LevelChunk) {
        if (chunkPos in mcLoadedChunks) return
        mcLoadedChunks[chunkPos] = chunk
        // 计算并写入区块实体方块高程索引
        chunkHeightIndex.computeAndPut(chunk, physicsLevel.mcLevel)
    }

    /**
     * 处理区块卸载事件（主线程调用）。
     *
     * 注意：不删除 chunkHeightIndex 中的索引条目。
     * 区块卸载后内存索引仍然保留，供物理线程查询未加载区块的地形信息（空间换时间）。
     */
    fun onChunkUnloaded(chunkPos: ChunkPos) {
        mcLoadedChunks.remove(chunkPos)
        unloadPhysicsChunk(chunkPos)
    }

    /**
     * 批量处理方块更新事件
     */
    fun onBlockUpdated(blockPositions: Set<BlockPos>) {
        val dirtySections = mutableSetOf<SectionPos>()
        // 收集受影响的 section，并按 chunk 分组以更新高程索引
        val chunkAffectedSections = mutableMapOf<ChunkPos, MutableSet<Int>>()
        blockPositions.forEach { blockPos ->
            val sectionPos = SectionPos.of(blockPos)
            dirtySections.add(sectionPos)
            // 记录该 chunk 中受影响的 sectionY
            val chunkPos = ChunkPos(blockPos)
            chunkAffectedSections.getOrPut(chunkPos) { mutableSetOf() }.add(sectionPos.y())

            physicsLevel.submitDeduplicatedTask("terrain_update_activate_${blockPos.toShortString()}", PPhase.ALL) {
                activator.setPhysicsLocation(
                    blockPos.toVec3().add(0.5, 0.5, 0.5).toBVector3f()
                )
                physicsLevel.world.contactTest(activator, this)
            }
        }
        markDirtySections(dirtySections)

        // 增量更新受影响的 chunk 的高程索引
        for ((chunkPos, sectionYs) in chunkAffectedSections) {
            val mcChunk = mcLoadedChunks[chunkPos] ?: continue
            for (sectionY in sectionYs) {
                chunkHeightIndex.updateSection(chunkPos, mcChunk, physicsLevel.mcLevel, sectionY)
            }
        }
    }

    // ========== Attachment 持久化 ==========

    /**
     * 将区块高程索引持久化到 ServerLevel 的 Attachment 中。
     * 在 LevelEvent.Save 时调用。
     */
    fun saveToAttachment(serverLevel: ServerLevel) {
        serverLevel.setData(
            SparkAttachments.CHUNK_SOLID_INTERVALS,
            chunkHeightIndex.toPersistentMap()
        )
    }

    /**
     * 从 ServerLevel 的 Attachment 中恢复区块高程索引。
     * 在 PhysicsLevelInitEvent 时调用。
     */
    fun loadFromAttachment(level: Level) {
        val data = level.getData(
            SparkAttachments.CHUNK_SOLID_INTERVALS
        )
        if (data.isNotEmpty()) {
            chunkHeightIndex.loadFromPersistentMap(data)
        }
    }

    // ========== 预约调度实现（基于 MC Ticket 系统） ==========

    /**
     * 预约在指定延迟后加载指定区块的指定 Y 范围地形，并在保持一定 tick 后自动释放。
     *
     * 多次调度同一 chunk 会取所有调度中最晚的 expire（自动合并）。
     * 内部通过 MC Ticket 系统（addRegionTicket）强制保持区块加载，
     * 到期后由 updateScheduledChunks 移除 ticket 并卸载地形。
     *
     * @param chunkPos 目标区块
     * @param yRange 需要加载的 section Y 范围（section 坐标）
     * @param delayTicks 延迟多少 tick 后开始激活 terrain（0 = 立即）
     * @param holdTicks terrain 激活后保持多少 tick。逾时自动释放
     *
     * 调用线程：任意线程（addRegionTicket 内部已线程安全）
     */
    fun scheduleTerrain(chunkPos: ChunkPos, yRange: IntRange, delayTicks: Int, holdTicks: Int) {
        val currentTick = physicsLevel.tickCount
        val expireTick = currentTick + delayTicks + holdTicks

        // 1. 记录此调度请求的过期时间（供 cancelAllSchedules 重算最晚过期）
        chunkActiveRequests.getOrPut(chunkPos, ::mutableListOf).add(expireTick)

        // 2. 合并最晚过期时间
        chunkExpireTicks.merge(chunkPos, expireTick) { old, new -> maxOf(old, new) }

        // 3. 立即添加 MC ticket 强制加载该 chunk（不等 delay，让 MC 提前准备）
        //    radius=2：地形构建可能依赖邻接 chunk 的方块数据
        val serverLevel = physicsLevel.mcLevel as? ServerLevel ?: return
        serverLevel.chunkSource.addRegionTicket(
            SPARK_TERRAIN_TICKET, chunkPos, 2, chunkPos
        )

        // 4. 提交去重延迟任务——到 delayTicks 时构建 PhysicsChunk 并激活
        val key = TERRAIN_LOAD_KEY_PREFIX + chunkPos
        physicsLevel.mcLevel.submitDelayedTask(key, PPhase.ALL, delayTicks) {
            loadAndActivateChunkTerrain(chunkPos, yRange)
        }
    }

    /**
     * 取消对某区块的全部调度请求，立即移除 MC ticket 并释放物理区块。
     *
     * 调用方：SparkLevel.cancelChunkLoad（Level 公开 API）。
     * 语义：取消该 chunk 的所有预约，立刻释放——投射物离开后立刻卸载。
     *
     * 调用线程：任意线程
     */
    fun cancelAllSchedules(chunkPos: ChunkPos) {
        chunkActiveRequests.remove(chunkPos)
        releaseChunkTerrain(chunkPos)
    }

    /**
     * 统一释放指定 chunk 的地形资源：
     * 1. 移除 MC ticket（告知 MC 可卸载该 chunk）
     * 2. 卸载物理区块（从物理世界移除）
     * 3. 清理过期追踪
     */
    private fun releaseChunkTerrain(chunkPos: ChunkPos) {
        // 1. 移除 MC ticket
        val serverLevel = physicsLevel.mcLevel as? ServerLevel
        serverLevel?.chunkSource?.removeRegionTicket(
            SPARK_TERRAIN_TICKET, chunkPos, 2, chunkPos
        )

        // 2. 卸载物理区块
        unloadPhysicsChunk(chunkPos)

        // 3. 清理追踪状态
        chunkExpireTicks.remove(chunkPos)
    }

    /**
     * 检查指定区块的指定 Y 范围地形是否已就绪。
     *
     * @return true = 地形刚体已在物理世界中可用（已加载 + 已构建 + 已激活）
     */
    fun isTerrainReady(chunkPos: ChunkPos, yRange: IntRange): Boolean {
        val physicsChunk = loadedChunks[chunkPos] ?: return false
        for (sectionY in yRange) {
            val section = physicsChunk.getSection(sectionY) ?: continue
            if (!section.isActive || !section.isBuilt()) return false
        }
        return true
    }

    /**
     * 每 tick 清理过期的调度请求。
     * 在 [updateActivation] 之后调用。
     *
     * 释放逻辑：对已到期的 chunk，移除 MC ticket 并卸载物理区块。
     */
    fun updateScheduledChunks() {
        if (chunkExpireTicks.isEmpty()) return
        val now = physicsLevel.tickCount
        val toRelease = chunkExpireTicks.filter { (_, expire) -> now >= expire }.keys.toSet()
        for (chunkPos in toRelease) {
            releaseChunkTerrain(chunkPos)
        }
    }

    /**
     * 实际执行区块的地形构建和激活（在延迟任务回调中调用）。
     *
     * 此时 MC ticket 已提前添加，chunk 由 MC ticket 系统保证已加载。
     * 仅需构建 PhysicsChunk 并激活指定 section 范围。
     */
    private fun loadAndActivateChunkTerrain(chunkPos: ChunkPos, yRange: IntRange) {
        val serverLevel = physicsLevel.mcLevel as? ServerLevel ?: return

        // MC ticket 已确保 chunk 加载，直接从缓存获取
        val mcChunk = serverLevel.getChunk(chunkPos.x, chunkPos.z) as? LevelChunk ?: return

        // 确保 mcLoadedChunks 中有记录
        if (chunkPos !in mcLoadedChunks) {
            mcLoadedChunks[chunkPos] = mcChunk
            // 同时计算高程索引（如果还没有）
            if (!chunkHeightIndex.hasChunk(chunkPos)) {
                chunkHeightIndex.computeAndPut(mcChunk, physicsLevel.mcLevel)
            }
        }

        // 如果物理区块尚未构建，则构建
        if (chunkPos !in loadedChunks) {
            val physicsChunk = PhysicsChunk(chunkPos, physicsLevel, mcChunk)
            physicsChunk.load()
            loadedChunks[chunkPos] = physicsChunk
        }

        // 激活指定 section 范围
        val physicsChunk = loadedChunks[chunkPos] ?: return
        physicsChunk.activateSectionsInRanges(listOf(yRange))
    }

    /**
     * 标记该section为脏, 待物理步进时更新
     */
    fun markDirtySections(sections: Set<SectionPos>) {
        dirtySections.addAll(sections)
    }

    /**
     * 更新所有脏section的碰撞体积
     * 在物理步进前调用
     *
     * 该方法会：
     * 1. 遍历所有脏section并更新其物理刚体
     * 2. 收集实际发生变化的section
     * 3. 将实际变化的section同步到客户端
     */
    fun updateDirtySections() {
        if (dirtySections.isEmpty()) return

        val sectionsToUpdate = dirtySections.toSet()
        dirtySections.clear()

        // 直接发送原始的脏section列表，不等待异步构建完成
        if (!physicsLevel.mcLevel.isClientSide && sectionsToUpdate.isNotEmpty()) {
            PacketDistributor.sendToPlayersInDimension(
                physicsLevel.mcLevel as ServerLevel,
                TerrainUpdatePayload(sectionsToUpdate)
            )
        }

        // 异步更新每个脏section
        sectionsToUpdate.forEach { sectionPos ->
            val chunkPos = ChunkPos(sectionPos.x(), sectionPos.z())
            val physicsChunk = loadedChunks[chunkPos] ?: return@forEach
            val sectionY = sectionPos.y()
            val physicsSection = physicsChunk.getSection(sectionY) ?: return@forEach
            // 开始异步更新
            physicsSection.startAsyncUpdate(this)
        }
    }

    fun loaded(pos: ChunkPos): Boolean {
        return pos in loadedChunks
    }

    /**
     * 获取性能统计信息
     */
    fun getStats(): String {
        return "物理区块: ${loadedChunks.size}, Section总数: $totalSections, " +
                "活跃Section: $activeSections, 索引Chunk数: ${chunkHeightIndex.size}"
    }

    /**
     * 清理所有资源
     */
    fun destroy() {
        // 先释放所有 MC ticket
        val serverLevel = physicsLevel.mcLevel as? ServerLevel
        chunkExpireTicks.keys.forEach { chunkPos ->
            serverLevel?.chunkSource?.removeRegionTicket(
                SPARK_TERRAIN_TICKET, chunkPos, 2, chunkPos
            )
        }

        loadedChunks.values.forEach { it.unload() }
        loadedChunks.clear()

        // 关闭线程池
        terrainBuilderScope.cancel()
        (terrainBuilderExecutor.executor as? ExecutorService)?.shutdownNow()

        // 清空调度状态
        chunkActiveRequests.clear()
        chunkExpireTicks.clear()
    }

    /**
     * 激活因地形破坏而受到影响的动态休眠刚体
     */
    override fun collision(event: PhysicsCollisionEvent) {
        val bodyA = event.objectA
        val bodyB = event.objectB
        var rigid : PhysicsRigidBody? = null
        if (bodyA == activator && bodyB is PhysicsRigidBody)
            rigid = bodyB
        else if (bodyB == activator && bodyA is PhysicsRigidBody)
            rigid = bodyA
        if (rigid == null) return
        if (rigid.isDynamic && !rigid.isActive)
            rigid.activate()
    }

    companion object {
        /**
         * Spark-Core 自定义地形强制加载 Ticket 类型。
         *
         * lifespan = 1200 一分钟长时间，完全由 scheduleTerrain / cancelAllSchedules 手动控制生命周期。
         * 不使用 TicketType.FORCED，因其会随 ForcedChunksSavedData 持久化到存档。
         */
        @JvmStatic
        val SPARK_TERRAIN_TICKET: TicketType<ChunkPos> = TicketType.create(
            "spark_terrain",
            Comparator.comparingLong(ChunkPos::toLong),
            1200  // 1分钟（20tps × 60s），ticket 最长存活时间。实际由 scheduleTerrain 的 holdTicks 提前释放
        )
    }
}
