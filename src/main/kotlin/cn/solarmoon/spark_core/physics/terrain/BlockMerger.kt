package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.math.Vector3f
import net.minecraft.world.level.block.state.BlockState

/**
 * 使用洪水填充算法合并相同形状的方块
 */
class BlockMerger(private val physicsLevel: PhysicsLevel) {

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
            val halfExtents = Vector3f(
                width / 2.0f,
                0.5f, // 完整方块高度为1，半高为0.5
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
    ) : Comparable<CandidateRect> {
        override fun compareTo(other: CandidateRect): Int {
            // 优先比较面积，面积相同则比较位置（确保稳定性）
            return if (area != other.area) {
                other.area - area // 面积大的优先
            } else {
                // 面积相同则按位置排序
                if (x != other.x) x - other.x else z - other.z
            }
        }
    }

    /**
     * 两阶段合并
     */
    fun mergeLayer(
        levelY: Int,
        blockStates: Array<Array<BlockState?>>
    ): List<MergedRect> {
        val mergedRects = mutableListOf<MergedRect>()

        // 第一阶段：最大矩形优先合并
        val phase1Rects = mergeXDirectionFirst(levelY, blockStates)

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
        val phase2Rects = mergeZDirectionRemains(levelY, blockStates, processed)
        mergedRects.addAll(phase2Rects)

        // 第三阶段：处理剩余的单个方块
        val phase3Rects = mergeSingleBlocks(levelY, blockStates, processed)
        mergedRects.addAll(phase3Rects)

        return mergedRects
    }

    /**
     * 第一阶段：x方向优先，合并尽可能大的矩形
     */
    private fun mergeXDirectionFirst(
        levelY: Int,
        blockStates: Array<Array<BlockState?>> // [16][16] 的二维数组
    ): List<MergedRect> {
        val mergedRects = mutableListOf<MergedRect>()
        val processed = Array(16) { BooleanArray(16) }

        // 预计算每个位置向右的连续相同形状长度
        val rightLengths = calculateRightLengths(levelY, blockStates)

        while (true) {
            // 寻找当前最大的可合并矩形
            val bestRect = findLargestMergeableRect(processed, rightLengths, blockStates, levelY)
            if (bestRect == null) break

            // 标记整个矩形为已处理
            markRectAsProcessed(processed, bestRect.x, bestRect.z, bestRect.width, bestRect.height)

            // 计算Y偏移
            val centerOffsetY = calculateCenterOffset(blockStates[bestRect.x][bestRect.z]!!)

            mergedRects.add(
                MergedRect(
                    bestRect.x, bestRect.z,
                    bestRect.x + bestRect.width - 1,
                    bestRect.z + bestRect.height - 1,
                    bestRect.shapeType,
                    centerOffsetY
                )
            )
        }
        return mergedRects
    }

    /**
     * 第二阶段：专门合并z方向连续的剩余方块
     */
    private fun mergeZDirectionRemains(
        levelY: Int,
        blockStates: Array<Array<BlockState?>>,
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

                val startState = blockStates[x][z]

                if (startState == null || !isMergeable(startState)) {
                    z++
                    continue
                }

                val startShapeType = physicsLevel.blockShapeManager.getShapeType(
                    startState.getBulletCollisionShape(physicsLevel)
                )

                // 寻找z方向连续的长度
                var endZ = z
                for (checkZ in z + 1 until 16) {
                    if (processed[x][checkZ]) break

                    val checkState = blockStates[x][checkZ] ?: break
                    if (!isMergeable(checkState)) break

                    val checkShapeType = physicsLevel.blockShapeManager.getShapeType(
                        checkState.getBulletCollisionShape(physicsLevel)
                    )
                    if (checkShapeType != startShapeType) break

                    endZ = checkZ
                }

                val length = endZ - z + 1

                // 只有当连续长度大于1时才合并（避免单个方块）
                if (length > 1) {
                    // 检查是否可以扩展到相邻的x列（形成更宽的条带）
                    val maxWidth = findMaxZStripWidth(x, z, endZ, blockStates, processed, startShapeType)

                    val centerOffsetY = calculateCenterOffset(startState)
                    mergedRects.add(
                        MergedRect(
                            x, z,
                            x + maxWidth - 1, endZ,
                            startShapeType,
                            centerOffsetY
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
        levelY: Int,
        blockStates: Array<Array<BlockState?>>,
        processed: Array<BooleanArray>
    ): List<MergedRect> {
        val singleRects = mutableListOf<MergedRect>()

        // 扫描所有位置，寻找未处理的单个方块
        for (x in 0 until 16) {
            for (z in 0 until 16) {
                // 跳过已处理的方块
                if (processed[x][z]) continue

                val blockState = blockStates[x][z] ?: continue

                if (!isMergeable(blockState)) continue

                val shapeType = physicsLevel.blockShapeManager.getShapeType(
                    blockState.getBulletCollisionShape(physicsLevel)
                )

                val centerOffsetY = calculateCenterOffset(blockState)

                // 创建1x1的矩形
                singleRects.add(
                    MergedRect(
                        x, z,
                        x, z, // 1x1矩形，所以maxX和maxZ与min相同
                        shapeType,
                        centerOffsetY
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
        blockStates: Array<Array<BlockState?>>,
        processed: Array<BooleanArray>,
        targetShapeType: Int
    ): Int {
        var maxWidth = 1

        // 向右扩展检查
        for (extendX in startX + 1 until 16) {
            // 检查从startZ到endZ的所有z坐标
            var canExtend = true
            for (checkZ in startZ..endZ) {
                if (extendX >= 16 || processed[extendX][checkZ]) {
                    canExtend = false
                    break
                }

                val state = blockStates[extendX][checkZ]

                if (state == null || !isMergeable(state)) {
                    canExtend = false
                    break
                }

                val shapeType = physicsLevel.blockShapeManager.getShapeType(
                    state.getBulletCollisionShape(physicsLevel)
                )
                if (shapeType != targetShapeType) {
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
     * 计算每个位置向右的连续相同形状长度
     * 用于快速判断矩形扩展的可能性
     */
    private fun calculateRightLengths(
        levelY: Int,
        blockStates: Array<Array<BlockState?>>
    ): Array<IntArray> {
        val rightLengths = Array(16) { IntArray(16) }

        // 从右向左计算连续长度
        for (z in 0 until 16) {
            var currentLength = 0
            for (x in 15 downTo 0) {
                val blockState = blockStates[x][z]
                if (blockState != null && isMergeable(blockState)) {
                    currentLength++
                } else {
                    currentLength = 0
                }
                rightLengths[x][z] = currentLength
            }
        }

        return rightLengths
    }

    /**
     * 寻找当前最大的可合并矩形
     */
    private fun findLargestMergeableRect(
        processed: Array<BooleanArray>,
        rightLengths: Array<IntArray>,
        blockStates: Array<Array<BlockState?>>,
        levelY: Int
    ): CandidateRect? {
        var bestRect: CandidateRect? = null

        // 遍历所有未处理的位置
        for (startZ in 0 until 16) {
            for (startX in 0 until 16) {
                if (processed[startX][startZ]) continue

                val startState = blockStates[startX][startZ] ?: continue
                if (!isMergeable(startState)) continue

                val shapeType = physicsLevel.blockShapeManager.getShapeType(
                    startState.getBulletCollisionShape(physicsLevel)
                )

                // 当前行的最大可能宽度
                val maxWidth = rightLengths[startX][startZ]

                // 尝试不同的高度，找到面积最大的矩形
                var currentHeight = 1
                var currentMinWidth = maxWidth

                // 向下扩展高度
                for (extendZ in startZ until 16) {
                    // 检查当前行是否可以被包含
                    if (extendZ > startZ) {
                        if (processed[startX][extendZ]) break
                        val extendState = blockStates[startX][extendZ]
                        if (extendState == null || !isMergeable(extendState)) break

                        val extendShapeType = physicsLevel.blockShapeManager.getShapeType(
                            extendState.getBulletCollisionShape(physicsLevel)
                        )
                        if (extendShapeType != shapeType) break

                        // 更新当前最小宽度
                        currentMinWidth = minOf(currentMinWidth, rightLengths[startX][extendZ])
                    }

                    // 计算当前矩形的实际宽度
                    val actualWidth = calculateActualWidth(
                        startX, startZ, extendZ, currentMinWidth,
                        processed, blockStates, shapeType
                    )

                    val currentArea = actualWidth * (extendZ - startZ + 1)

                    // 更新最佳矩形
                    if (bestRect == null || currentArea > bestRect.area) {
                        bestRect = CandidateRect(
                            startX, startZ,
                            actualWidth, extendZ - startZ + 1,
                            shapeType,
                            currentArea
                        )
                    }

                    // 如果宽度已经为1，再增加高度也不会增加面积
                    if (currentMinWidth == 1) break
                }
            }
        }

        return bestRect
    }

    /**
     * 计算矩形的实际可用宽度
     */
    private fun calculateActualWidth(
        startX: Int, startZ: Int, currentZ: Int, maxPossibleWidth: Int,
        processed: Array<BooleanArray>,
        blockStates: Array<Array<BlockState?>>,
        targetShapeType: Int
    ): Int {
        var actualWidth = maxPossibleWidth

        // 检查从startZ到currentZ的所有行，确保宽度一致
        for (checkZ in startZ..currentZ) {
            var rowWidth = 0
            for (checkX in startX until startX + maxPossibleWidth) {
                if (checkX >= 16 || processed[checkX][checkZ]) break

                val state = blockStates[checkX][checkZ]
                if (state == null || !isMergeable(state)) break

                val checkShapeType = physicsLevel.blockShapeManager.getShapeType(
                    state.getBulletCollisionShape(physicsLevel)
                )
                if (checkShapeType != targetShapeType) break

                rowWidth++
            }
            actualWidth = minOf(actualWidth, rowWidth)

            // 如果某行的宽度已经小于当前实际宽度，提前返回
            if (actualWidth < maxPossibleWidth) {
                return actualWidth
            }
        }

        return actualWidth
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

    /**
     * 检查方块是否可合并
     */
    private fun isMergeable(blockState: BlockState): Boolean {
        val shape = blockState.getBulletCollisionShape(physicsLevel)
        return physicsLevel.blockShapeManager.isMergeableShape(shape)
    }

    /**
     * 计算形状的Y中心偏移
     */
    private fun calculateCenterOffset(blockState: BlockState): Float {
        val shape = blockState.getBulletCollisionShape(physicsLevel)
        return when (shape) {
            physicsLevel.blockShapeManager.UP_HALF_BLOCK -> 0.25f
            physicsLevel.blockShapeManager.DOWN_HALF_BLOCK -> -0.25f
            else -> 0f
        }
    }
}