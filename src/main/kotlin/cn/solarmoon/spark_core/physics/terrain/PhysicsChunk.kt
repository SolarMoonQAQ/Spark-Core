package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 管理一个完整区块（16x256x16）的物理表示
 * 将区块划分为多个section进行独立管理
 *
 * @property chunkPos 区块坐标
 * @property physicsLevel 所属物理世界
 * @property chunk 对应的MC区块对象，仅在构造时使用
 */
class PhysicsChunk(
    val chunkPos: ChunkPos,
    val physicsLevel: PhysicsLevel,
    val chunk: LevelChunk
) {
    private val sections = mutableMapOf<Int, PhysicsChunkSection>()
    private var isLoaded = false

    /**
     * 当前应激活的 section Y 坐标集合（去重缓存）。
     * 供 [activateSectionsInRanges] 判断激活范围是否变化，避免无变化时重复执行 activate/deactivate。
     */
    private val activeSectionYs = mutableSetOf<Int>()

    /**
     * 异步加载区块物理表示
     *
     * 懒构建：仅创建 section 对象并预填充构建快照（廉价主线程操作），
     * 不立即排队构建作业。构建推迟到 section 首次 [PhysicsChunkSection.activate] 时触发，
     * 避免区块加载时一次性将全部约 20 个 section 的构建作业排队到单线程构建队列
     * （无载具场景下 TerrainShapeBuilder 线程被饱和的关键因素之一）。
     */
    fun load() {
        if (isLoaded) return

        val minSection = physicsLevel.mcLevel.minSection
        val maxSection = physicsLevel.mcLevel.maxSection

        for (sectionY in minSection until maxSection) {
            val sectionPos = SectionPos.of(chunkPos, sectionY)
            val physicsSection = PhysicsChunkSection(sectionPos, physicsLevel, chunk)

            // 预填充构建快照（廉价），懒构建作业在首次激活时才排队
            physicsSection.prepareSnapshot()
            sections[sectionY] = physicsSection
        }

        isLoaded = true
    }

    /**
     * 卸载区块，清理所有资源
     */
    fun unload() {
        sections.values.forEach {it.destroy()}
        sections.clear()
        activeSectionYs.clear()
        isLoaded = false
    }

    /**
     * 一次性激活指定范围内的所有section
     */
    fun activateSectionsInRanges(ranges: List<IntRange>) {
        // 计算目标激活集合，与当前缓存比较，无变化则直接跳过
        val targetYs = HashSet<Int>()
        sections.values.forEach { section ->
            val sectionY = section.sectionPos.y
            if (ranges.any { range -> sectionY in range }) targetYs.add(sectionY)
        }
        if (targetYs == activeSectionYs) return

        activeSectionYs.clear()
        activeSectionYs.addAll(targetYs)
        sections.values.forEach { section ->
            val sectionY = section.sectionPos.y
            if (sectionY in targetYs) {
                section.activate()
            } else {
                // 如果section当前是激活状态但不在新范围内，则停用
                if (section.isActive) {
                    section.deactivate()
                } else {
                    // 未激活但可能在懒构建中：清除待激活标记，避免构建完成后自动加入物理世界
                    section.cancelPendingActivation()
                }
            }
        }
    }

    /**
     * 一次性停用指定范围内的所有section
     */
    fun deactivateSectionsInRanges(ranges: List<IntRange>) {
        sections.values.forEach { section ->
            val sectionY = section.sectionPos.y
            val shouldDeactivate = ranges.any { range -> sectionY in range }

            if (shouldDeactivate && section.isActive) {
                section.deactivate()
                activeSectionYs.remove(sectionY)
            }
        }
    }

    /**
     * 激活所有section
     */
    fun activateAll() {
        sections.values.forEach { it.activate() }
    }

    /**
     * 停用所有section
     */
    fun deactivateAll() {
        sections.values.forEach { it.deactivate() }
        // 同步清空激活缓存，否则后续 activateSectionsInRanges 会被缓存误判为“无变化”而跳过重新激活
        activeSectionYs.clear()
    }

    /**
     * 是否有任意激活的 section（O(1) 判断，基于激活缓存）。
     * 供 PhysicsChunkManager 增量遍历确定候选 chunk 是否仍需要检查停用。
     */
    fun hasActiveSections(): Boolean = activeSectionYs.isNotEmpty()

    /**
     * 获取指定位置的section
     */
    fun getSection(sectionY: Int): PhysicsChunkSection? = sections[sectionY]

    /**
     * 获取指定方块位置的section
     */
    fun getSectionForBlockPos(blockPos: BlockPos): PhysicsChunkSection? {
        val sectionY = SectionPos.blockToSectionCoord(blockPos.y)
        return sections[sectionY]
    }

    /**
     * 获取所有活跃的section数量
     */
    fun getActiveSectionCount(): Int = sections.values.count { it.isActive && !it.isEmpty() }

    /**
     * 获取所有section数量
     */
    fun getTotalSectionCount(): Int = sections.size
}