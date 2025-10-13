package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.math.Vector3f
import io.netty.util.internal.ConcurrentSet
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.network.PacketDistributor
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
) {
    // 已加载的物理区块（包含构建状态）
    private val loadedChunks = mutableMapOf<ChunkPos, PhysicsChunk>()

    // Minecraft已加载的区块（不构建物理表示）
    private val mcLoadedChunks = mutableMapOf<ChunkPos, LevelChunk>()

    // 脏section管理
    private val dirtySections = ConcurrentSet<SectionPos>()

    // 地形构建线程池
    private val terrainBuilderExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    ) { r -> Thread(r, "TerrainShapeBuilder-${physicsLevel.name}") }.asCoroutineDispatcher()

    val terrainBuilderScope = CoroutineScope(
        terrainBuilderExecutor + SupervisorJob() +
                CoroutineExceptionHandler { _, exception ->
                    SparkCore.LOGGER.error("地形构建线程异常", exception)
                }
    )

    // 配置参数
    private val buildRadius = 2 // 构建半径（区块数）
    private val activationRadius = 8 // 激活半径（方块数）

    // 性能统计
    private val totalSections: Int
        get() = loadedChunks.values.sumOf { it.getTotalSectionCount() }
    private val activeSections: Int
        get() = loadedChunks.values.sumOf { it.getActiveSectionCount() }

    /**
     * 加载指定区块的物理表示
     */
    private fun loadChunk(chunkPos: ChunkPos, chunk: LevelChunk) {
        if (chunkPos in mcLoadedChunks) return
        mcLoadedChunks[chunkPos] = chunk
    }

    /**
     * 卸载指定区块的物理表示
     */
    private fun unloadChunk(chunkPos: ChunkPos) {
        mcLoadedChunks.remove(chunkPos)
        val chunk = loadedChunks[chunkPos] ?: return

        // 从脏section集合中移除该区块的所有section
        dirtySections.removeAll { it == chunkPos }

        chunk.unload()
        loadedChunks.remove(chunkPos)
    }

    /**
     * 根据刚体位置更新构建和激活范围
     */
    fun updateBuildAndActivation(boundingBoxes: List<AABB>) {

        // 1. 收集需要构建的区块
        val chunksToBuild = collectChunksToBuild(boundingBoxes)

        // 2. 开始构建新区块
        startBuildingChunks(chunksToBuild)

        // 3. 更新激活状态（只激活已构建的区块）
        updateActivation(boundingBoxes)

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
    private fun updateActivation(boundingBoxes: List<AABB>) {
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
     */
    fun onChunkLoaded(chunkPos: ChunkPos, chunk: LevelChunk) {
        loadChunk(chunkPos, chunk)
    }

    /**
     * 处理区块卸载事件（主线程调用）
     */
    fun onChunkUnloaded(chunkPos: ChunkPos) {
        unloadChunk(chunkPos)
    }

    /**
     * 批量处理方块更新事件
     */
    fun onBlockUpdated(blockPositions: Set<BlockPos>) {
        val dirtySections = mutableSetOf<SectionPos>()
        blockPositions.forEach { blockPos ->
            val sectionPos = SectionPos.of(blockPos)
            dirtySections.add(sectionPos)
        }
        markDirtySections(dirtySections)
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
                "活跃Section: $activeSections"
    }

    /**
     * 清理所有资源
     */
    fun destroy() {
        loadedChunks.values.forEach { it.unload() }
        loadedChunks.clear()

        // 关闭线程池
        terrainBuilderScope.cancel()
        (terrainBuilderExecutor.executor as? ExecutorService)?.shutdownNow()

    }
}