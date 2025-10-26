package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.util.BlockCollisionUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * 区块section的快照，用于在后台线程安全地构建碰撞形状
 * 使用固定大小数组存储方块信息以节省内存
 */
class SectionSnapshot private constructor(
    val blockSnapshots: Array<BlockSnapshot?>,//比起存BlockPos作为索引，使用数组更节省内存
    val pos: SectionPos
) {

    companion object {
        /**
         * 空的SectionSnapshot实例，所有空section共享此实例以节省内存
         */
        @JvmStatic
        val EMPTY = SectionSnapshot(Array(1) { null }, SectionPos.of(0, Int.MIN_VALUE, 0))

        /**
         * 从区块创建快照（主线程调用）
         */
        @JvmStatic
        fun snapshotFromChunk(level: Level, chunk: LevelChunk, pos: SectionPos): SectionSnapshot {
            val sectionIndex = level.getSectionIndexFromSectionY(pos.y())

            // 检查section索引是否有效
            if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) {
                return EMPTY
            }

            val section = chunk.sections[sectionIndex]
            // 如果是空section或只有空气，返回空快照
            if (section == null || section.hasOnlyAir()) {
                return EMPTY
            }

            val blockSnapshots = arrayOfNulls<BlockSnapshot>(4096)
            val childShapeIndex = ArrayList<Int>(16)
            var hasBlocks = false
            var i = 0
            // 遍历section内所有方块
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    for (z in 0 until 16) {
                        val index = getIndexFromRelativePos(x, y, z)
                        val blockState = section.getBlockState(x, y, z)

                        // 跳过空气和没有碰撞体积的方块
                        if (!blockState.isAir && !blockState.getCollisionShape(
                                EmptyBlockGetter.INSTANCE,
                                BlockPos.ZERO
                            ).isEmpty
                        ) {
                            blockSnapshots[index] = BlockSnapshot(
                                blockState,
                                BlockCollisionUtil.getBlockFriction(chunk.level, blockState, getWorldPos(pos, x, y, z)),
                                BlockCollisionUtil.getBlockRollingFriction(
                                    chunk.level,
                                    blockState,
                                    getWorldPos(pos, x, y, z)
                                ),
                                BlockCollisionUtil.getRestitution(chunk, blockState, getWorldPos(pos, x, y, z)),
                                BlockCollisionUtil.getSlip(chunk, blockState, getWorldPos(pos, x, y, z))
                            )
                            childShapeIndex.add(index)
                            hasBlocks = true
                            i++
                        }
                    }
                }
            }

            // 如果没有有效方块，返回空实例
            return if (hasBlocks) SectionSnapshot(blockSnapshots, pos) else EMPTY
        }

        /**
         * 将相对坐标转换为数组索引
         */
        @JvmStatic
        fun getIndexFromRelativePos(x: Int, y: Int, z: Int): Int {
            require(x in 0..15 && y in 0..15 && z in 0..15) {
                "Coordinates must be in range 0-15, got x=$x, y=$y, z=$z"
            }
            return x + (y shl 4) + (z shl 8)
        }

        /**
         * 将数组索引转换为相对坐标
         */
        @JvmStatic
        fun getRelativePosFromIndex(index: Int): BlockPos {
            require(index in 0..4095) { "Index must be in range 0-4095, got $index" }
            val x = index and 0xF
            val y = (index shr 4) and 0xF
            val z = (index shr 8) and 0xF
            return BlockPos(x, y, z)
        }

        /**
         * 将相对坐标转换为世界坐标
         */
        @JvmStatic
        fun getWorldPos(sectionPos: SectionPos, relativeX: Int, relativeY: Int, relativeZ: Int): BlockPos {
            return sectionPos.origin().offset(relativeX, relativeY, relativeZ)
        }

    }

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

    /**
     * 将世界坐标转换为相对坐标
     */
    fun getRelativePos(blockPos: BlockPos): BlockPos {
        return blockPos.subtract(pos.origin())
    }

    /**
     * 将世界坐标转换为相对坐标
     */
    fun getWorldPos(relativePos: BlockPos): BlockPos {
        return relativePos.offset(pos.origin())
    }

    /**
     * 通过相对坐标获取方块快照
     */
    fun getBlockSnapshot(relativeX: Int, relativeY: Int, relativeZ: Int): BlockSnapshot? {
        return blockSnapshots[getIndexFromRelativePos(relativeX, relativeY, relativeZ)]
    }

    /**
     * 通过相对坐标获取方块快照
     */
    fun getBlockSnapshot(relativePos: BlockPos): BlockSnapshot? {
        return getBlockSnapshot(relativePos.x, relativePos.y, relativePos.z)
    }

    /**
     * 通过世界坐标获取方块快照
     */
    fun getBlockSnapshotFromWorld(worldPos: BlockPos): BlockSnapshot? {
        val relativePos = getRelativePos(worldPos)
        return getBlockSnapshot(relativePos)
    }

    /**
     * 检查是否为空快照
     */
    fun isEmpty(): Boolean {
        return this === EMPTY
    }

    /**
     * 获取非空方块的数量
     */
    fun getBlockCount(): Int {
        if (isEmpty()) return 0
        return blockSnapshots.count { it != null }
    }

    fun copy(): SectionSnapshot {
        return SectionSnapshot(blockSnapshots.clone(), pos)
    }
}