package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import kotlin.math.abs

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
    private val loadedChunks = mutableMapOf<ChunkPos, PhysicsChunk>()

    // 性能统计
    private var totalSections = 0
    private var activeSections = 0

    /**
     * 加载指定区块的物理表示
     */
    private fun loadChunk(chunkPos: ChunkPos, chunk: LevelChunk) {
        if (chunkPos in loadedChunks) return

        val physicsChunk = PhysicsChunk(chunkPos, physicsLevel, chunk)
        physicsChunk.load()
        loadedChunks[chunkPos] = physicsChunk

        totalSections += physicsChunk.getTotalSectionCount()
    }

    /**
     * 卸载指定区块的物理表示
     */
    private fun unloadChunk(chunkPos: ChunkPos) {
        val chunk = loadedChunks[chunkPos] ?: return

        totalSections -= chunk.getTotalSectionCount()
        activeSections -= chunk.getActiveSectionCount()

        chunk.unload()
        loadedChunks.remove(chunkPos)
    }

    /**
     * 根据BoundingBox列表更新区块激活状态
     * 统一激活所有BoundingBox范围内的section，停用范围外的section
     */
    fun updateActivation(boundingBoxes: List<AABB>) {
        if (boundingBoxes.isEmpty()) {
            // 如果没有BoundingBox，停用所有section
            loadedChunks.values.forEach { it.deactivateAll() }
            activeSections = 0
            return
        }

        // 使用Map记录每个区块需要激活的Y范围
        val activationMap = mutableMapOf<ChunkPos, MutableSet<IntRange>>()

        // 收集所有需要激活的区块和section范围
        boundingBoxes.forEach { aabb ->
            // 计算BoundingBox覆盖的区块和section范围
            val minChunkX = SectionPos.blockToSectionCoord(aabb.minX.toInt())
            val maxChunkX = SectionPos.blockToSectionCoord(aabb.maxX.toInt())
            val minChunkZ = SectionPos.blockToSectionCoord(aabb.minZ.toInt())
            val maxChunkZ = SectionPos.blockToSectionCoord(aabb.maxZ.toInt())

            // 计算BoundingBox覆盖的section Y范围
            val minSectionY = SectionPos.blockToSectionCoord(aabb.minY.toInt())
            val maxSectionY = SectionPos.blockToSectionCoord(aabb.maxY.toInt())
            val sectionRange = minSectionY..maxSectionY

            // 为每个受影响的区块添加激活范围
            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    val chunkPos = ChunkPos(chunkX, chunkZ)
                    activationMap.getOrPut(chunkPos) { mutableSetOf() }.add(sectionRange)
                }
            }
        }

        var newActiveSections = 0

        loadedChunks.values.forEach { chunk ->
            val chunkPos = chunk.chunkPos
            val activationRanges = activationMap[chunkPos]

            if (!activationRanges.isNullOrEmpty()) {
                // 合并该区块的所有激活范围
                val mergedRanges = mergeRanges(activationRanges)
                // 激活合并后的范围
                mergedRanges.forEach { range ->
                    chunk.activateSections(range.first, range.last)
                }
                newActiveSections += chunk.getActiveSectionCount()
            } else {
                // 停用不在激活范围内的区块
                chunk.deactivateAll()
            }
        }

        activeSections = newActiveSections
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
     * 处理方块更新事件（主线程调用）
     */
    fun onBlockUpdated(blockPos: BlockPos, oldState: BlockState, newState: BlockState) {
        val chunkPos = ChunkPos(blockPos)
        loadedChunks[chunkPos]?.onBlockUpdated(blockPos, oldState, newState)
    }

    /**
     * 获取性能统计信息
     */
    fun getStats(): String {
        return "物理区块: ${loadedChunks.size}, Section总数: $totalSections, 活跃Section: $activeSections"
    }

    /**
     * 清理所有资源
     */
    fun destroy() {
        loadedChunks.values.forEach { it.unload() }
        loadedChunks.clear()
        totalSections = 0
        activeSections = 0
    }
}