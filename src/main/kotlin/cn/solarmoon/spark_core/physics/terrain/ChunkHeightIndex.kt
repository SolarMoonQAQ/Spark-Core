package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.util.BlockCollisionUtil
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

/**
 * 区块实体方块高程索引。
 *
 * 以 chunkPos.toLong() 为键，ShortArray 编码的 Y 区间列表为值（升序排列，互不重叠）。
 * 内存常驻，随 MC 区块加载计算、方块变更时增量更新。
 * 区块卸载时不删除索引数据——保证物理线程能查询未加载区块的地形信息（空间换时间）。
 *
 * 线程安全：主线程写入（compute/update），物理线程只读（query）。
 * 写入时替换整个 ShortArray 引用，严禁原地修改数组内容。
 *
 * @see [区块实体方块高程索引——设计与实施计划]
 */
class ChunkHeightIndex {

    /** ChunkPos.toLong() → 升序排列的 Y 区间 ShortArray [min1, max1, min2, max2, ...] */
    val intervals: ConcurrentHashMap<Long, ShortArray> = ConcurrentHashMap()

    // ========== 查询（物理线程 & 主线程安全） ==========

    /**
     * 检查指定 chunk 在指定 Y 区间内是否有实体方块。
     * 二分查找，O(log N)。
     *
     * @param chunkPos 区块坐标
     * @param minY 查询 Y 下界（MC 坐标）
     * @param maxY 查询 Y 上界（MC 坐标）
     * @return true = 存在实体方块；false = 无数据或不存在
     */
    fun hasSolidInRange(chunkPos: ChunkPos, minY: Short, maxY: Short): Boolean {
        val arr = intervals[chunkPos.toLong()] ?: return false
        // 对区间索引二分：arr = [min0, max0, min1, max1, ...]，共 n 个区间，区间 i 的 min/max 在 arr[2i]/arr[2i+1]
        val n = arr.size / 2
        var lo = 0
        var hi = n - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val iMin = arr[mid * 2]
            val iMax = arr[mid * 2 + 1]
            if (maxY < iMin) {
                hi = mid - 1
            } else if (minY > iMax) {
                lo = mid + 1
            } else {
                return true  // 有交集
            }
        }
        return false
    }

    /**
     * 获取指定 chunk 的区间数组，无数据返回 null。
     * 调用方不得修改返回的数组（共享引用，线程安全约束）。
     */
    fun getIntervals(chunkPos: ChunkPos): ShortArray? = intervals[chunkPos.toLong()]

    /** 检查指定 chunk 是否已有索引数据。 */
    fun hasChunk(chunkPos: ChunkPos): Boolean =  intervals.containsKey(chunkPos.toLong())

    /** 索引中已记录的 chunk 总数。 */
    val size: Int get() = intervals.size

    // ========== 写入（仅主线程，由 PhysicsChunkManager 调用） ==========

    /**
     * 计算并存储某 chunk 的实体方块 Y 区间。
     * 遍历所有 section，合并相邻含方块 section。
     */
    fun computeAndPut(chunk: LevelChunk, level: Level) {
        val arr = computeIntervals(chunk, level)
        intervals[chunk.pos.toLong()] = arr
    }

    /**
     * 增量更新某 chunk 的某 section 后重新合并区间。
     *
     * 只重算指定的一个 section 是否有实体方块，然后针对性地修改已有区间：
     * - 空→实体：在区间列表中插入该 section 的 Y 范围，检查与相邻区间合并
     * - 实体→空：从覆盖该 section 的区间中移除对应部分（可能导致区间分裂或缩小）
     * - 无变化：不做任何修改
     *
     * 相比全量重算（扫描 24 个 section），此方法只扫描 1 个 section + O(n) 区间列表操作。
     */
    fun updateSection(chunkPos: ChunkPos, chunk: LevelChunk, level: Level, sectionY: Int) {
        val packedPos = chunkPos.toLong()
        val existingArr = intervals[packedPos]
        if (existingArr == null) {
            // 无已有数据（理论上不应出现，因为该 chunk 至少加载过一次），全量计算兜底
            computeAndPut(chunk, level)
            return
        }

        val nowHasSolid = sectionHasSolidBlock(chunk, level, sectionY)
        val secMinY = (sectionY shl 4).toShort()
        val secMaxY = ((sectionY shl 4) + 15).toShort()

        // 查找此 section 的 Y 范围之前是否已被某个区间覆盖
        var wasSolid = false
        var coverIdx = -1  // 覆盖了 sectionY 的区间在 existingArr 中的偶数起始索引
        for (i in existingArr.indices step 2) {
            if (secMinY >= existingArr[i] && secMaxY <= existingArr[i + 1]) {
                wasSolid = true
                coverIdx = i
                break
            }
        }

        if (nowHasSolid == wasSolid) return  // 状态无变化，无需任何操作

        val newRanges = mutableListOf<Pair<Short, Short>>()

        if (nowHasSolid && !wasSolid) {
            // 该 section 从空变为有实体方块：在区间列表中插入并合并相邻
            // 收集所有已有区间 + 新区间，排序后合并重叠/相邻区间
            val allRanges = mutableListOf<Pair<Short, Short>>()
            for (i in existingArr.indices step 2) {
                allRanges.add(existingArr[i] to existingArr[i + 1])
            }
            allRanges.add(secMinY to secMaxY)
            allRanges.sortBy { it.first }

            mergeSortedRanges(allRanges, newRanges)

        } else {
            // !nowHasSolid && wasSolid：该 section 从有实体方块变为空
            // 从覆盖的区间中移除 secMinY..secMaxY，可能导致区间分裂或缩小
            for (i in existingArr.indices step 2) {
                if (i == coverIdx) {
                    val min = existingArr[i]
                    val max = existingArr[i + 1]
                    // 区间前半部分（如果有）
                    if (min < secMinY) {
                        newRanges.add(min to (secMinY - 1).toShort())
                    }
                    // 区间后半部分（如果有）
                    if (max > secMaxY) {
                        newRanges.add((secMaxY + 1).toShort() to max)
                    }
                    // 如果 min==secMinY && max==secMaxY，前后都不添加 → 该区间被完整移除
                } else {
                    newRanges.add(existingArr[i] to existingArr[i + 1])
                }
            }
        }

        val newArr = ShortArray(newRanges.size * 2).also { arr ->
            newRanges.forEachIndexed { i, (min, max) ->
                arr[i * 2] = min
                arr[i * 2 + 1] = max
            }
        }
        intervals[packedPos] = newArr
    }

    /** 导出全部数据用于持久化。 */
    fun toPersistentMap(): MutableMap<Long, ShortArray> = ConcurrentHashMap(intervals)

    /** 从持久化数据恢复。 */
    fun loadFromPersistentMap(data: MutableMap<Long, ShortArray>) {
        intervals.clear()
        intervals.putAll(data)
    }

    // ========== 内部实现 ==========

    /**
     * 全量扫描 chunk 的所有 section 并计算实体方块 Y 区间。
     */
    private fun computeIntervals(chunk: LevelChunk, level: Level): ShortArray {
        val minSection = level.minSection
        val maxSection = level.maxSection
        val ranges = mutableListOf<Pair<Short, Short>>()
        val EMPTY_SENTINEL: Short = (-1).toShort()

        var rangeStart: Short = EMPTY_SENTINEL
        for (secY in minSection until maxSection) {
            val hasSolid = sectionHasSolidBlock(chunk, level, secY)
            if (hasSolid && rangeStart == EMPTY_SENTINEL) {
                rangeStart = (secY shl 4).toShort()  // section 起始 Y
            } else if (!hasSolid && rangeStart != EMPTY_SENTINEL) {
                ranges.add(rangeStart to ((secY shl 4) - 1).toShort())  // 上一 section 结束 Y
                rangeStart = EMPTY_SENTINEL
            }
        }
        if (rangeStart != EMPTY_SENTINEL) {
            ranges.add(rangeStart to ((maxSection shl 4) - 1).toShort())
        }

        return ShortArray(ranges.size * 2).also { arr ->
            ranges.forEachIndexed { i, (min, max) ->
                arr[i * 2] = min
                arr[i * 2 + 1] = max
            }
        }
    }

    /**
     * 合并已按 first 升序排列的重叠/相邻区间列表。
     * 将结果写入目标列表（调用方已创建）。
     */
    private fun mergeSortedRanges(
        sorted: List<Pair<Short, Short>>,
        out: MutableList<Pair<Short, Short>>
    ) {
        var cur = sorted[0]
        for (j in 1 until sorted.size) {
            val next = sorted[j]
            if (next.first <= cur.second + 1) {
                // 重叠或相邻（如 [-64,0] 和 [1,16]），合并
                cur = cur.first to maxOf(cur.second, next.second)
            } else {
                out.add(cur)
                cur = next
            }
        }
        out.add(cur)
    }

    /**
     * 判断某个 section 内是否存在实体方块（有碰撞体积的非空气方块）。
     *
     * 优化策略（两层过滤）：
     * 1. 调色板预检：若 section.hasOnlyAir() → 直接返回 false，跳过 4096 次遍历
     * 2. 对非纯空气 section，逐方块遍历，一旦找到 hasCollision() == true 即早期退出
     */
    private fun sectionHasSolidBlock(chunk: LevelChunk, level: Level, secY: Int): Boolean {
        val sectionIndex = level.getSectionIndexFromSectionY(secY)
        if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) return false
        val section = chunk.sections[sectionIndex]
        // 调色板预检：纯空气 section 直接跳过（覆盖下界空洞区、主世界高空/地下空洞区）
        if (section.hasOnlyAir()) return false

        val baseY = secY shl 4
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                for (z in 0 until 16) {
                    val state = chunk.getBlockState(BlockPos(x, baseY + y, z))
                    if (BlockCollisionUtil.hasCollision(state)) return true  // 早期退出
                }
            }
        }
        return false
    }
}


