package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.util.BlockCollisionUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 区块section的快照，用于在后台线程安全地构建碰撞形状
 * 包含section内所有非空气方块的BlockState，位置以及其他用于物理计算的信息
 */
data class SectionSnapshot(
    val shapes: MutableMap<BlockPos, BlockSnapshot>,
    val pos: SectionPos
) {

    /**
     * 单个方块的形状信息和可用于物理计算的信息
     */
    data class BlockSnapshot(
        val state: BlockState,
        val friction: Float, // 静摩擦系数
        val rollingFriction: Float, // 滚动摩擦系数
        val restitution: Float, // 弹性系数，0~1，0表示无弹性，1表示完全弹性
        var slip: Float // 湿滑系数，0~1，0表示完全干燥，1表示完全浸润
    )

    fun getBlockSnapshot(relativePos: BlockPos): BlockSnapshot? {
        val worldPos = pos.origin().offset(relativePos)
        return shapes[worldPos]
    }

    companion object {
        /**
         * 从区块创建快照（主线程调用）
         */
        @JvmStatic
        fun snapshotFromChunk(level: Level, chunk: LevelChunk, pos: SectionPos): SectionSnapshot {
            val sectionIndex = level.getSectionIndexFromSectionY(pos.y())

            // 检查section索引是否有效
            if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) {
                return SectionSnapshot(mutableMapOf(), pos)
            }

            val section = chunk.sections[sectionIndex]
            // 如果是空section或只有空气，返回空快照
            if (section == null || section.hasOnlyAir()) {
                return SectionSnapshot(mutableMapOf(), pos)
            }

            val blockCaches = mutableMapOf<BlockPos, BlockSnapshot>()
            val origin = pos.origin() // section的世界坐标原点

            // 遍历section内所有方块
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    for (z in 0 until 16) {
                        val worldPos = origin.offset(x, y, z)
                        val blockState = section.getBlockState(x, y, z)

                        // 跳过空气和没有碰撞体积的方块
                        if (!blockState.isAir && !blockState.getCollisionShape(level, worldPos).isEmpty) {
                            blockCaches[worldPos] =
                                BlockSnapshot(
                                    blockState,
                                    BlockCollisionUtil.getBlockFriction(chunk.level, blockState, worldPos),
                                    BlockCollisionUtil.getBlockRollingFriction(chunk.level, blockState, worldPos),
                                    BlockCollisionUtil.getRestitution(chunk, blockState, worldPos),
                                    BlockCollisionUtil.getSlip(chunk, blockState, worldPos)
                                )
                        }
                    }
                }
            }

            return SectionSnapshot(blockCaches, pos)
        }
    }
}