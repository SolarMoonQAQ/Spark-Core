package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
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
     * 加载区块物理表示
     * 为每个section构建碰撞形状（主线程调用）
     */
    fun load() {
        if (isLoaded) return

        val minSection = physicsLevel.mcLevel.minSection
        val maxSection = physicsLevel.mcLevel.maxSection

        for (sectionY in minSection until maxSection) {
            val sectionPos = SectionPos.of(chunkPos, sectionY)
            val physicsSection = PhysicsChunkSection(sectionPos, physicsLevel, chunk)

            // 构建碰撞形状，只有非空section才会创建刚体
            if (physicsSection.buildCollisionShape()) {
                physicsSection.createPhysicsBody()
                sections[sectionY] = physicsSection
            }
        }

        isLoaded = true
    }

    /**
     * 卸载区块，清理所有资源
     */
    fun unload() {
        sections.values.forEach { it.destroy() }
        sections.clear()
        isLoaded = false
    }

    /**
     * 激活指定Y范围内的section
     */
    fun activateSections(minY: Int, maxY: Int) {
        sections.values.forEach { section ->
            if (section.worldPos.y in minY..maxY) {
                section.activate()
            }
        }
    }

    /**
     * 停用指定Y范围内的section
     */
    fun deactivateSections(minY: Int, maxY: Int) {
        sections.values.forEach { section ->
            if (section.worldPos.minBlockY() in minY..maxY) {
                section.deactivate()
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
    }

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
     * 处理方块更新（主线程调用）
     */
    fun onBlockUpdated(blockPos: BlockPos, oldState: BlockState, newState: BlockState) {
        val sectionY = SectionPos.blockToSectionCoord(blockPos.y)
        val section = sections[sectionY]

        section?.onBlockUpdated(blockPos, newState)
    }

    /**
     * 获取所有活跃的section数量
     */
    fun getActiveSectionCount(): Int = sections.values.count { it.isActive }

    /**
     * 获取所有section数量
     */
    fun getTotalSectionCount(): Int = sections.size
}