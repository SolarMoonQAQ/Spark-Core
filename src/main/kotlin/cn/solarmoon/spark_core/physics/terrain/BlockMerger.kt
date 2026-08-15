package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.math.Vector3f
import net.minecraft.world.level.block.state.BlockState

/**
 * 使用洪水填充算法合并相同形状的方块
 */
class BlockMerger(private val physicsLevel: PhysicsLevel) {

    companion object {
        /** 不可合并/空位 */
        private const val TYPE_NONE = 0
        /** 完整方块 */
        private const val TYPE_FULL = 1
        /** 上半砖（碰撞盒位于 Y∈[0.5,1]，中心偏移 +0.25） */
        private const val TYPE_UPPER_HALF = 2
        /** 下半砖（碰撞盒位于 Y∈[0,0.5]，中心偏移 -0.25） */
        private const val TYPE_LOWER_HALF = 3

        /**
         * 由形状类型直接推导 Y 中心偏移。
         * 等价于原先每次对同一方块重复计算 VoxelShape 的 calculateCenterOffset：
         * 完整方块 -> 0，上半砖(2) -> +0.25，下半砖(3) -> -0.25。
         */
        private fun centerOffsetFor(shapeType: Int): Float = when (shapeType) {
            TYPE_UPPER_HALF -> 0.25f
            TYPE_LOWER_HALF -> -0.25f
            else -> 0f
        }
    }

    /**
     * 表示一个合并后的矩形区域
     */
    data class MergedRect(
        val minX: Int, val minZ: Int,
        val maxX: Int, val maxZ: Int,
        val shapeType: Int,
        val centerOffsetY: Float = 0f // 用于半砖等非完整方块的Y偏移
    ) {
        val width: Int get() = maxX - minX + 1
        val depth: Int get() = maxZ - minZ + 1

        /**
         * 转换为碰撞形状
         */
        fun toCollisionShape(): BoxCollisionShape {
            var height = when(shapeType){
                1 -> 0.5f // 完整方块
                2,3 -> 0.25f // 半砖
                else -> 0.5f
            }
            val halfExtents = Vector3f(
                width / 2.0f,
                height,
                depth / 2.0f
            )
            return BoxCollisionShape(halfExtents)
        }

        /**
         * 获取形状中心位置（相对于section原点）
         */
        fun getCenter(levelY: Int): Vector3f {
            return Vector3f(
                minX + width / 2.0f - 8f,
                levelY + 0.5f + centerOffsetY - 8f, // 应用Y偏移
                minZ + depth / 2.0f - 8f
            )
        }
    }

    /**
     * 表示一个候选矩形区域
     */
    private data class CandidateRect(
        val x: Int, val z: Int,
        val width: Int, val height: Int,
        val shapeType: Int,
        val area: Int = width * height
    )

    /**
     * 三阶段合并
     */
    fun mergeLayer(
        levelY: Int,
        blockStates: Array<Array<BlockState?>>
    ): List<MergedRect> {
        // 预计算 16×16 形状类型矩阵：每个方块仅计算一次 VoxelShape，
        // 避免合并过程中对同一方块反复执行 isMergeableShape + getShapeType（性能热点）
        val typeMatrix = Array(16) { x ->
            IntArray(16) { z ->
                blockStates[x][z]
                    ?.let { physicsLevel.blockShapeManager.getMergeableShapeType(it) }
                    ?: TYPE_NONE
            }
        }

        val mergedRects = mutableListOf<MergedRect>()

        // 第一阶段：最大矩形优先合并
        val phase1Rects = mergeXDirectionFirst(typeMatrix)

        // 过滤掉1x1的矩形，只保留较大的矩形
        val (largeRects, smallRects) = phase1Rects.partition { rect ->
            rect.width > 1 || rect.depth > 1
        }
        mergedRects.addAll(largeRects)

        val processed = Array(16) { BooleanArray(16) }
        // 只标记较大矩形区域为已处理，1x1矩形区域保持未处理状态
        for (rect in largeRects) {
            for (z in rect.minZ..rect.maxZ) {
                for (x in rect.minX..rect.maxX) {
                    processed[x][z] = true
                }
            }
        }

        // 第二阶段：专门合并z方向连续方块（包括第一阶段留下的1x1矩形）
        val phase2Rects = mergeZDirectionRemains(typeMatrix, processed)
        mergedRects.addAll(phase2Rects)

        // 第三阶段：处理剩余的单个方块
        val phase3Rects = mergeSingleBlocks(typeMatrix, processed)
        mergedRects.addAll(phase3Rects)

        return mergedRects
    }

    /**
     * 第一阶段：x方向优先，合并尽可能大的矩形
     */
    private fun mergeXDirectionFirst(
        typeMatrix: Array<IntArray> // [16][16] 的形状类型矩阵
    ): List<MergedRect> {
        val mergedRects = mutableListOf<MergedRect>()
        val processed = Array(16) { BooleanArray(16) }

        while (true) {
            // 寻找当前最大的可合并矩形（直方图法，单次 O(16×16×类型数)）
            val bestRect = findLargestMergeableRect(processed, typeMatrix)
            if (bestRect == null) break

            // 标记整个矩形为已处理
            markRectAsProcessed(processed, bestRect.x, bestRect.z, bestRect.width, bestRect.height)

            mergedRects.add(
                MergedRect(
                    bestRect.x, bestRect.z,
                    bestRect.x + bestRect.width - 1,
                    bestRect.z + bestRect.height - 1,
                    bestRect.shapeType,
                    centerOffsetFor(bestRect.shapeType)
                )
            )
        }
        return mergedRects
    }

    /**
     * 第二阶段：专门合并z方向连续的剩余方块
     */
    private fun mergeZDirectionRemains(
        typeMatrix: Array<IntArray>,
        processed: Array<BooleanArray>
    ): List<MergedRect> {
        val mergedRects = mutableListOf<MergedRect>()

        // 按x列扫描
        for (x in 0 until 16) {
            var z = 0
            while (z < 16) {
                // 跳过已处理的方块
                if (processed[x][z]) {
                    z++
                    continue
                }

                val startShapeType = typeMatrix[x][z]
                if (startShapeType == TYPE_NONE) {
                    z++
                    continue
                }

                // 寻找z方向连续的长度
                var endZ = z
                for (checkZ in z + 1 until 16) {
                    if (processed[x][checkZ]) break
                    if (typeMatrix[x][checkZ] != startShapeType) break
                    endZ = checkZ
                }

                val length = endZ - z + 1

                // 只有当连续长度大于1时才合并（避免单个方块）
                if (length > 1) {
                    // 检查是否可以扩展到相邻的x列（形成更宽的条带）
                    val maxWidth = findMaxZStripWidth(x, z, endZ, typeMatrix, processed, startShapeType)

                    mergedRects.add(
                        MergedRect(
                            x, z,
                            x + maxWidth - 1, endZ,
                            startShapeType,
                            centerOffsetFor(startShapeType)
                        )
                    )

                    // 标记为已处理
                    for (currentZ in z..endZ) {
                        for (currentX in x until x + maxWidth) {
                            if (currentX < 16) {
                                processed[currentX][currentZ] = true
                            }
                        }
                    }

                    z = endZ + 1
                } else {
                    z++
                }
            }
        }

        return mergedRects
    }

    /**
     * 第三阶段：处理剩余的单个方块
     */
    private fun mergeSingleBlocks(
        typeMatrix: Array<IntArray>,
        processed: Array<BooleanArray>
    ): List<MergedRect> {
        val singleRects = mutableListOf<MergedRect>()

        // 扫描所有位置，寻找未处理的单个方块
        for (x in 0 until 16) {
            for (z in 0 until 16) {
                // 跳过已处理的方块
                if (processed[x][z]) continue

                val shapeType = typeMatrix[x][z]
                if (shapeType == TYPE_NONE) continue

                // 创建1x1的矩形
                singleRects.add(
                    MergedRect(
                        x, z,
                        x, z, // 1x1矩形，所以maxX和maxZ与min相同
                        shapeType,
                        centerOffsetFor(shapeType)
                    )
                )

                // 标记为已处理（虽然这一步不是必须的，但为了完整性）
                processed[x][z] = true
            }
        }

        return singleRects
    }

    /**
     * 查找z方向条带的最大可能宽度
     */
    private fun findMaxZStripWidth(
        startX: Int, startZ: Int, endZ: Int,
        typeMatrix: Array<IntArray>,
        processed: Array<BooleanArray>,
        targetShapeType: Int
    ): Int {
        var maxWidth = 1

        // 向右扩展检查
        for (extendX in startX + 1 until 16) {
            // 检查从startZ到endZ的所有z坐标
            var canExtend = true
            for (checkZ in startZ..endZ) {
                if (processed[extendX][checkZ] || typeMatrix[extendX][checkZ] != targetShapeType) {
                    canExtend = false
                    break
                }
            }

            if (canExtend) {
                maxWidth++
            } else {
                break
            }
        }

        return maxWidth
    }

    /**
     * 寻找当前最大的可合并矩形（直方图法）
     *
     * 对每种形状类型维护柱状图：heights[x] 表示列 x 从当前底行 z 向上
     * 连续未被处理且类型相同的方块数。对每个底行 z 用单调栈在 O(16) 内
     * 求出该柱状图的最大矩形，整体单次 O(16×16×类型数)。
     */
    private fun findLargestMergeableRect(
        processed: Array<BooleanArray>,
        typeMatrix: Array<IntArray>
    ): CandidateRect? {
        var bestRect: CandidateRect? = null
        val heights = IntArray(16)

        for (shapeType in TYPE_FULL..TYPE_LOWER_HALF) {
            heights.fill(0)
            for (z in 0 until 16) {
                // 更新柱状图高度（已处理或类型不匹配则清零）
                for (x in 0 until 16) {
                    heights[x] =
                        if (!processed[x][z] && typeMatrix[x][z] == shapeType) heights[x] + 1 else 0
                }
                // 以 z 为底行求该柱状图的最大矩形
                val rect = largestRectInHistogram(heights, z, shapeType)
                if (rect != null && (bestRect == null || rect.area > bestRect.area)) {
                    bestRect = rect
                }
            }
        }
        return bestRect
    }

    /**
     * 单调栈求柱状图的最大矩形（O(16)）
     *
     * @param bottomZ 底行索引，矩形在 z 方向的顶行为 bottomZ - rectHeight + 1
     */
    private fun largestRectInHistogram(
        heights: IntArray,
        bottomZ: Int,
        shapeType: Int
    ): CandidateRect? {
        var maxArea = 0
        var bestX = 0
        var bestWidth = 0
        var bestHeight = 0
        val stack = IntArray(17)
        var top = -1

        for (x in 0..16) {
            // x == 16 时用 0 作为哨兵，强制弹出栈中剩余柱
            val h = if (x < 16) heights[x] else 0
            while (top >= 0 && h < heights[stack[top]]) {
                val col = stack[top]
                top--
                val rectHeight = heights[col]
                val left = if (top >= 0) stack[top] + 1 else 0
                val width = x - left
                val area = rectHeight * width
                if (area > maxArea) {
                    maxArea = area
                    bestX = left
                    bestWidth = width
                    bestHeight = rectHeight
                }
            }
            top++
            stack[top] = x
        }

        if (maxArea == 0) return null
        return CandidateRect(
            bestX, bottomZ - bestHeight + 1,
            bestWidth, bestHeight, shapeType, maxArea
        )
    }

    /**
     * 标记矩形区域为已处理
     */
    private fun markRectAsProcessed(
        processed: Array<BooleanArray>,
        startX: Int, startZ: Int, width: Int, height: Int
    ) {
        for (z in startZ until startZ + height) {
            for (x in startX until startX + width) {
                if (x < 16 && z < 16) {
                    processed[x][z] = true
                }
            }
        }
    }
}
