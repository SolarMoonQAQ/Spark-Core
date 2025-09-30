package cn.solarmoon.spark_core.physics.terrain

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 区块section的快照，用于在后台线程安全地构建碰撞形状
 * 包含section内所有非空气方块的BlockState和位置信息
 */
data class ChunkSnapshot(
    val shapes: List<BlockSnapshot>,
    val pos: SectionPos
) {

    /**
     * 单个方块的形状信息
     */
    data class BlockSnapshot(
        val state: BlockState,
        val localPos: BlockPos
    )

    companion object {
        /**
         * 从区块创建快照（主线程调用）
         */
        @JvmStatic
        fun snapshotFromChunk(level: Level, chunk: LevelChunk, pos: SectionPos): ChunkSnapshot {
            val sectionIndex = level.getSectionIndexFromSectionY(pos.y())

            // 检查section索引是否有效
            if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) {
                return ChunkSnapshot(emptyList(), pos)
            }

            val section = chunk.sections[sectionIndex]
            // 如果是空section或只有空气，返回空快照
            if (section == null || section.hasOnlyAir()) {
                return ChunkSnapshot(emptyList(), pos)
            }

            val blockCaches = mutableListOf<BlockSnapshot>()
            val origin = pos.origin() // section的世界坐标原点

            // 遍历section内所有方块
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    for (z in 0 until 16) {
                        val localPos = BlockPos(x, y, z)
                        val worldPos = origin.offset(x, y, z)
                        val blockState = section.getBlockState(x, y, z)

                        // 跳过空气和没有碰撞体积的方块
                        if (!blockState.isAir && !blockState.getCollisionShape(level, worldPos).isEmpty) {
                            blockCaches.add(BlockSnapshot(blockState, localPos))
                        }
                    }
                }
            }

            return ChunkSnapshot(blockCaches, pos)
        }

        /**
         * 从单个方块更新创建快照（用于增量更新）
         */
        @JvmStatic
        fun snapshotFromBlockUpdate(pos: SectionPos, blockPos: BlockPos, blockState: BlockState): ChunkSnapshot {
            val localPos = BlockPos(
                blockPos.x and 15,
                blockPos.y and 15,
                blockPos.z and 15
            )

            return if (!blockState.isAir && !blockState.getCollisionShape(null, blockPos).isEmpty) {
                ChunkSnapshot(listOf(BlockSnapshot(blockState, localPos)), pos)
            } else {
                // 如果方块变成空气，返回空列表表示需要移除
                ChunkSnapshot(emptyList(), pos)
            }
        }
    }
}