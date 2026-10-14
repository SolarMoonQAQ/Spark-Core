package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.util.BlockCollisionUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 区块section的快照，用于在后台线程安全地构建碰撞形状
 * 使用固定大小数组存储方块信息以节省内存
 *
 * ## 双通道设计（重要）
 *
 * 本类承载**两条完全独立**的数据通道，由同一次 4096-voxel 遍历产出：
 *
 * - **通道 A（固体）**：[blockSnapshots]，判据是「非空气且有非空碰撞形状」，
 *   语义是接触力学（摩擦/弹性/湿滑），供 `BlockMerger` 构建地形刚体。
 * - **通道 B（流体）**：[fluidIndex]，判据是「`BlockState.getFluidState()` 非空」，
 *   语义是浮力/阻力（密度/粘度/液面高度），**不参与**任何碰撞构建。
 *
 * 二者**正交**：含水方块（waterlogged slab）两条通道同时命中；
 * 海带/海草只命中 B；干石头只命中 A。因此不允许把流体挂在 `BlockSnapshot` 上，
 * 也不允许用 A 的结果推导 B（反之亦然）。
 *
 * @property blockSnapshots 通道 A：逐 voxel 的固体快照，null = 该 voxel 无固体
 * @property fluidIndex 通道 B：section 级流体索引，null = 该 section 无流体
 * @property hasSolid 通道 A 是否非空
 * @property hasFluid 通道 B 是否非空
 */
class SectionSnapshot private constructor(
    val blockSnapshots: Array<BlockSnapshot?>,//比起存BlockPos作为索引，使用数组更节省内存
    val fluidIndex: SectionFluidIndex?,
    val pos: SectionPos,
    val hasSolid: Boolean,
    val hasFluid: Boolean
) {

    companion object {
        /** 无固体方块的 section 共享此只读空数组，`copy()` 时无需克隆 16~32 KB */
        private val EMPTY_BLOCKS: Array<BlockSnapshot?> = arrayOfNulls(4096)

        /**
         * 空的SectionSnapshot实例，所有空section共享此实例以节省内存
         *
         * 语义：**无固体且无流体**。注意纯水 section 不是 EMPTY —— 它有流体通道内容。
         *
         * 数组长度统一为 4096（与常规快照一致），避免 `getBlockSnapshot` 索引 4095 时越界。
         */
        @JvmStatic
        val EMPTY = SectionSnapshot(EMPTY_BLOCKS, null, SectionPos.of(0, Int.MIN_VALUE, 0), false, false)

        /**
         * 从区块创建快照（主线程调用）
         *
         * 循环内复用 `BlockPos.MutableBlockPos`，消除最坏 4096 次 `BlockPos` / `SectionPos.origin()`
         * 分配（与《地形方块变更热路径优化建议.md》S0 同源）。该 scratch 是方法局部变量，
         * 天然主线程独占。
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
            // （LevelChunkSection.hasOnlyAir() 判据是 nonEmptyBlockCount == 0，而水是一个 Block，
            //  因此纯水 section 不会被这里筛掉 —— 这条早退对本类的流体通道是安全的）
            if (section == null || section.hasOnlyAir()) {
                return EMPTY
            }

            val blockSnapshots = arrayOfNulls<BlockSnapshot>(4096)
            val fluidBuilder = SectionFluidIndex.Builder()
            val scratch = BlockPos.MutableBlockPos()
            val originX = pos.minBlockX()
            val originY = pos.minBlockY()
            val originZ = pos.minBlockZ()
            var hasSolid = false
            var i = 0
            // 遍历section内所有方块
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    for (z in 0 until 16) {
                        val index = getIndexFromRelativePos(x, y, z)
                        val blockState = section.getBlockState(x, y, z)

                        // 空气 voxel：双通道都不可能命中，整体跳过
                        //   - 通道 A：空气无碰撞形状
                        //   - 通道 B：Block#getFluidState 对 air / cave_air / void_air 恒返回 Fluids.EMPTY
                        if (blockState.isAir) continue

                        // ---- 通道 A：固体（判据：有非空碰撞形状）----
                        if (!blockState.getCollisionShape(
                                EmptyBlockGetter.INSTANCE,
                                BlockPos.ZERO
                            ).isEmpty
                        ) {
                            // 一次 DataMap 查找获取全部物理属性数据，避免重复查询
                            val worldPos = scratch.set(originX + x, originY + y, originZ + z)
                            val physicsData = BlockCollisionUtil.getPhysicsData(blockState)
                            blockSnapshots[index] = BlockSnapshot(
                                blockState,
                                physicsData.friction,
                                physicsData.rollingFriction,
                                physicsData.restitution,
                                BlockCollisionUtil.calcSlip(physicsData, chunk, worldPos)
                            )
                            hasSolid = true
                            i++
                        }

                        // ---- 通道 B：流体（判据：FluidState 非空，与 A 正交）----
                        // 不能用 A 的结果推导：含水方块 A、B 同时命中；海带/海草只命中 B。
                        val fluidState = blockState.fluidState   // 无分配：非流体返回静态空实例
                        if (!fluidState.isEmpty) {
                            val h = if (y < 15) {
                                // 上方 voxel 在本 section 内：直接读 PalettedContainer，零分配。
                                // 与 FlowingFluid#hasSameAbove 的语义逐字一致。
                                val above = section.getBlockState(x, y + 1, z).fluidState
                                if (fluidState.type.isSame(above.type)) 1.0f else fluidState.ownHeight
                            } else {
                                // 局部 y == 15：跨 section，走原版 BlockGetter（每 section 最多 256 次）
                                fluidState.getHeight(level, scratch.set(originX + x, originY + y, originZ + z))
                            }
                            fluidBuilder.mark(
                                x, y, z,
                                fluidState.type,
                                (h * SectionFluidIndex.Q).roundToInt()
                            )
                            if (y == 15) {
                                // 跨 section 续接标记：判据是「上方是同类流体」。
                                // 不能用 topQ == 16 判定 —— 静水顶层是 8/9，量化后为 14，会永远判 false。
                                val above = level.getFluidState(
                                    scratch.set(originX + x, originY + y + 1, originZ + z)
                                )
                                if (fluidState.type.isSame(above.type)) {
                                    fluidBuilder.markReachesTop(x, z)
                                }
                            }
                        }
                    }
                }
            }

            // 无固体且无流体才返回 EMPTY —— 纯水 section 有流体通道内容，不是 EMPTY
            val hasFluid = fluidBuilder.hasAny()
            return if (hasSolid || hasFluid)
                SectionSnapshot(blockSnapshots, fluidBuilder.build(), pos, hasSolid, hasFluid)
            else EMPTY
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
        val friction: Float,           // 静摩擦系数
        val rollingFriction: Float,    // 滚动摩擦系数
        val restitution: Float,        // 弹性系数，0~1
        val slip: Float                // 湿滑系数，0~1
    ) {
        /**
         * 返回一个修改了 slip 的新 BlockSnapshot 实例
         * @param newSlip 新的湿滑系数
         * @return 新的快照对象，其他属性保持不变
         */
        fun withSlip(newSlip: Float): BlockSnapshot {
            return copy(slip = newSlip)
        }
    }

    /**
     * 将世界坐标转换为相对坐标
     *
     * **注意：这是夹取（clamp）语义，不是「越界返回 null」。**
     * 采样点落在相邻 chunk / 相邻 section 时会被静默夹到边界格，
     * 返回**另一个格子**的数据。对固体通道（相邻方块级抖动）无伤大雅，
     * 但**流体通道禁止使用本方法** —— 必须走 strict 转换，见 `SectionFluidIndex.sampleLocal`。
     */
    fun getRelativePos(blockPos: BlockPos): BlockPos {
        val x = max(min(blockPos.x - pos.origin().x, 15), 0)
        val y = max(min(blockPos.y - pos.origin().y, 15), 0)
        val z = max(min(blockPos.z - pos.origin().z, 15), 0)
        return BlockPos(x, y, z)
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
        val index = getIndexFromRelativePos(relativeX, relativeY, relativeZ)
        return blockSnapshots[index]
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

    /**
     * 复制快照。
     *
     * [fluidIndex] **共享引用不克隆**（实例不可变）。
     * 无固体方块时连 [blockSnapshots] 也共享只读空数组 —— 那 4096 个格子全是 null，
     * 克隆纯属浪费；流水每 tick 触发整节重建时，这一项每次省 16~32 KB。
     *
     * 安全性：`refreshSlipIfNeeded()` 只对**非空**槽位写入，`markRemoved()` 写的是 `null`，
     * 对全 null 的共享数组都是无操作，且两者都被 `isEmpty()` 提前挡住。
     */
    fun copy(): SectionSnapshot {
        val blocks = if (hasSolid) blockSnapshots.clone() else EMPTY_BLOCKS
        return SectionSnapshot(blocks, fluidIndex, pos, hasSolid, hasFluid)
    }
}
