package cn.solarmoon.spark_core.physics.terrain.merge

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.terrain.TerrainChunkPos3D
import cn.solarmoon.spark_core.physics.terrain.config.TerrainMergeConfig
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import cn.solarmoon.spark_core.physics.collision.VoxelShapeConverter
import net.minecraft.world.phys.shapes.BooleanOp

/**
 * 地形合并管理器
 * 负责管理和执行地形碰撞块的合并操作
 */
class TerrainMergeManager(
    private val physicsLevel: PhysicsLevel,
    private val config: TerrainMergeConfig = TerrainMergeConfig()
) {
    // 合并后的碰撞体缓存
    private val mergedBodies = ConcurrentHashMap<BlockPos, PhysicsRigidBody>()
    
    // 性能指标收集器
    private val metrics = TerrainPerformanceMetrics()
    
    // 合并操作计数器
    private var mergeAttempts = 0
    private var mergeSuccesses = 0

    // 锁定集合，用于防止并发修改同一区块
    private val activeLocks = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * 尝试为操作获取锁
     * @param lockKey 锁的唯一标识符
     * @return 如果成功获取锁，返回true；否则返回false
     */
    internal fun acquireLock(lockKey: String): Boolean {
        return activeLocks.add(lockKey)
    }
    
    /**
     * 释放之前获取的锁
     * @param lockKey 锁的唯一标识符
     */
    internal fun releaseLock(lockKey: String) {
        activeLocks.remove(lockKey)
    }
    
    /**
     * 计算两个方块的相似度
     */
    private fun calculateBlockSimilarity(block1: BlockPos, block2: BlockPos): Float {
        val state1 = physicsLevel.mcLevel.getBlockState(block1)
        val state2 = physicsLevel.mcLevel.getBlockState(block2)
        
        // 基础相似度
        var similarity = 1.0f
        
//        // 根据方块类型调整相似度
//        if (state1.block != state2.block) {
//            similarity *= 0.5f
//        }
//
//        // 根据属性调整相似度
//        if (state1.properties.size != state2.properties.size) {
//            similarity *= 0.7f
//        }
//
//        // 根据碰撞形状调整相似度
//        val shape1 = state1.getCollisionShape(physicsLevel.mcLevel, block1)
//        val shape2 = state2.getCollisionShape(physicsLevel.mcLevel, block2)
//        if (shape1 != shape2) {
//            similarity *= 0.6f
//        }
        
        return similarity
    }
    
    /**
     * 从方块组创建VoxelShape
     * 将一组连通的方块合并为单个VoxelShape
     */
    private fun createVoxelShapeFromBlocks(blocks: List<BlockPos>): VoxelShape {
        // 初始化一个空的VoxelShape
        var resultShape = Shapes.empty()
        
        // 遍历所有方块，获取它们的碰撞形状并合并
        for (pos in blocks) {
            try {
                val blockState = physicsLevel.mcLevel.getBlockState(pos)
                if (!blockState.isAir) {
                    // 获取方块的碰撞形状
                    val blockShape = blockState.getCollisionShape(physicsLevel.mcLevel, pos)
                    if (!blockShape.isEmpty()) {
                        // 将形状移动到方块的实际位置
                        val movedShape = blockShape.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                        // 合并到结果形状中
                        resultShape = Shapes.joinUnoptimized(resultShape, movedShape, BooleanOp.OR)
                    }
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取方块形状时出错: $pos", e)
            }
        }
        
        // 优化结果形状
        return resultShape.optimize()
    }
    
    /**
     * 创建合并的碰撞体
     * @param blocks 要合并的方块列表
     * @return 创建的物理刚体
     */
    private fun createMergedBody(blocks: List<BlockPos>): PhysicsRigidBody? {
        if (blocks.isEmpty()) return null
        
        try {

            
            // 创建一个代表这组方块中心的位置点(用于命名和跟踪)
            val centerBlock = blocks.first()
            val name = "merged_${centerBlock.x}_${centerBlock.y}_${centerBlock.z}"
            
            // 从方块组创建VoxelShape
            val combinedShape = createVoxelShapeFromBlocks(blocks)
            
            if (combinedShape.isEmpty()) {
                SparkCore.LOGGER.debug("生成的VoxelShape为空，跳过创建碰撞体")
                return null
            }
            
            // 使用VoxelShapeConverter将VoxelShape转换为CollisionShape
            val collisionShape = VoxelShapeConverter.toCollisionShape(combinedShape)
            
            // 直接创建PhysicsRigidBody，而不是通过VoxelShapeConverter
            val rigidBody = PhysicsRigidBody(
                name,
                physicsLevel.mcLevel,
                collisionShape,
                0f // 静态刚体
            )
            
            // 设置碰撞体属性
            rigidBody.setUserIndex(config.bodyExpirationTime) // 设定销毁倒计时
            
            // 确保碰撞检测被启用
            rigidBody.setCollisionGroup(1)
            rigidBody.setCollideWithGroups(1)
            
            // 设置位置
            val bounds = calculateMergedBounds(blocks)
            val location = Vector3f(
                (bounds.min.x + bounds.max.x) / 2,
                (bounds.min.y + bounds.max.y) / 2,
                (bounds.min.z + bounds.max.z) / 2
            )
            rigidBody.setPhysicsLocation(location)
            
            // 将碰撞体添加到物理世界
            physicsLevel.world.add(rigidBody)
            
            // 将位置标记为已合并
            blocks.forEach { pos ->
                mergedBodies[pos] = rigidBody
            }
            SparkCore.LOGGER.debug("创建合并碰撞体完成，包含 ${blocks.size} 个方块")
            return rigidBody
        } catch (e: Exception) {
            SparkCore.LOGGER.error("使用VoxelShape创建合并碰撞体失败", e)
            return null
        }
    }
    
    /**
     * 计算合并后的边界框
     */
    private fun calculateMergedBounds(blocks: List<BlockPos>): BoundingBox {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        
        blocks.forEach { pos ->
            minX = min(minX, pos.x)
            minY = min(minY, pos.y)
            minZ = min(minZ, pos.z)
            maxX = max(maxX, pos.x)
            maxY = max(maxY, pos.y)
            maxZ = max(maxZ, pos.z)
        }
        
        return BoundingBox(
            Vector3f(minX.toFloat(), minY.toFloat(), minZ.toFloat()),
            Vector3f(maxX.toFloat() + 1, maxY.toFloat() + 1, maxZ.toFloat() + 1)
        )
    }
    
    /**
     * 移除指定位置的合并碰撞体
     */
    fun removeMergedBody(pos: BlockPos) {
        val body = mergedBodies.remove(pos)
        if (body != null) {
            // 检查这个碰撞体是否还被其他位置引用
            val isStillReferenced = mergedBodies.values.any { it === body }
            
            // 只有当不再被引用时才从世界中移除
            if (!isStillReferenced) {
                physicsLevel.submitTask {
                    try {
                        if (physicsLevel.world.pcoList.contains(body)) {
                            physicsLevel.world.remove(body)
                        }
                    } catch (e: Exception) {
                        //SparkCore.LOGGER.debug("移除合并碰撞体失败: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * 获取性能报告
     */
    fun getPerformanceReport(): String {
        return """
            地形合并性能报告:
            平均碰撞时间: ${metrics.averageCollisionTime}ms
            内存使用率: ${metrics.memoryUsage * 100}%
            合并效率: ${metrics.calculateMergeEfficiency() * 100}%
            合并成功率: ${metrics.mergeSuccessRate * 100}%
            平均合并时间: ${metrics.averageMergeTime}ms
            原始碰撞体数量: ${metrics.originalBodyCount}
            合并后碰撞体数量: ${metrics.mergedBodyCount}
        """.trimIndent()
    }

    /**
     * 查找与指定方块相连的一组方块
     * 使用简单的空间邻近性搜索
     */
    private fun findConnectedBlocks(startBlock: BlockPos, allBlocks: List<BlockPos>, processedBlocks: Set<BlockPos>): List<BlockPos> {
        val connectedBlocks = mutableListOf<BlockPos>()
        val queue = mutableListOf(startBlock)
        val visited = mutableSetOf<BlockPos>()
        visited.add(startBlock)
        
        // 限制每个连通区域的最大大小
        val maxRegionSize = 4096
        
        while (queue.isNotEmpty() && connectedBlocks.size < maxRegionSize) {
            val current = queue.removeAt(0)
            
            // 寻找相邻的方块
            val neighbors = findNeighbors(current, allBlocks)
            for (neighbor in neighbors) {
                if (neighbor !in visited && neighbor !in processedBlocks) {
                    // 检查方块相似度
                    val similarity = calculateBlockSimilarity(startBlock, neighbor)
                    if (similarity >= config.mergeThreshold) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                        connectedBlocks.add(neighbor)
                        
                        if (connectedBlocks.size >= maxRegionSize) break
                    }
                }
            }
        }
        
        return connectedBlocks
    }

    /**
     * 查找指定方块的相邻方块
     */
    private fun findNeighbors(block: BlockPos, allBlocks: List<BlockPos>): List<BlockPos> {
        // 检查水平和垂直邻居
        val potentialNeighbors = listOf(
            BlockPos(block.x + 1, block.y, block.z),
            BlockPos(block.x - 1, block.y, block.z),
            BlockPos(block.x, block.y, block.z + 1),
            BlockPos(block.x, block.y, block.z - 1)
        ) + if (config.enableVerticalMerge) {
            listOf(
                BlockPos(block.x, block.y + 1, block.z),
                BlockPos(block.x, block.y - 1, block.z)
            )
        } else {
            emptyList()
        }
        
        return potentialNeighbors.filter { it in allBlocks }
    }


    /**
     * 按相邻性将方块分组
     */
    private fun groupBlocksByAdjacency(blocks: List<BlockPos>): List<List<BlockPos>> {
        val groups = mutableListOf<List<BlockPos>>()
        val processedBlocks = mutableSetOf<BlockPos>()
        
        for (block in blocks) {
            if (block in processedBlocks) continue
            
            // 查找与当前方块相连的所有方块
            val connected = findConnectedBlocks(block, blocks, processedBlocks)
            if (connected.isNotEmpty()) {
                // 将当前方块加入到连通块列表中
                val group = connected + block
                groups.add(group)
                processedBlocks.addAll(group)
            } else {
                // 如果没有相连的方块，单独成组
                groups.add(listOf(block))
                processedBlocks.add(block)
            }
        }
        
        return groups
    }

    /**
     * 合并三维区块内的方块
     * 处理指定的三维区块，在垂直方向上限制高度范围
     */
    fun mergeChunk3D(chunkPos3D: TerrainChunkPos3D) {
        val startTime = System.nanoTime()
        val timeoutMillis = 5000L // 设置超时时间为5秒
        
        try {
            // 输出合并区块日志
            SparkCore.LOGGER.info("[诊断] 准备合并三维区块: $chunkPos3D")
        } catch (e: Throwable) {
            // 捕获所有异常，包括NoClassDefFoundError等
            System.err.println("输出诊断日志时出错: ${e.javaClass.name}: ${e.message}")
            e.printStackTrace(System.err)
        }
        
        // 使用区块位置作为锁定标识符
        val lockKey = "merge_lock_${chunkPos3D}"
        
        // 检查是否已经有线程在处理该区块
        if (!acquireLock(lockKey)) {
            try {
                SparkCore.LOGGER.debug("区块 $chunkPos3D 已经有其他线程在处理，跳过")
            } catch (e: Throwable) {
                System.err.println("诊断日志异常: ${e.javaClass.name}: ${e.message}")
            }
            return
        }
        
        try {
            // 超时检测
            val shouldTimeout = {
                try {
                    val elapsedTime = (System.nanoTime() - startTime) / 1_000_000
                    if (elapsedTime > timeoutMillis) {
                        SparkCore.LOGGER.warn("合并三维区块 $chunkPos3D 超时，已执行 ${elapsedTime}ms")
                        true
                    } else false
                } catch (e: Throwable) {
                    System.err.println("超时检测异常: ${e.javaClass.name}: ${e.message}")
                    false
                }
            }
            
            SparkCore.LOGGER.info("开始合并三维区块: $chunkPos3D")
            
            // 获取区块对应的Y坐标范围
            val yRange = chunkPos3D.getYRange()
            
            // 计算区块在世界中的实际坐标范围
            val minX = chunkPos3D.x * 16
            val maxX = minX + 15
            val minZ = chunkPos3D.z * 16
            val maxZ = minZ + 15
            val minY = yRange.first
            val maxY = yRange.last
            
            // 收集该区块范围内的所有实体方块
            val blocks = mutableListOf<BlockPos>()
            
            for (x in minX..maxX) {
                if (shouldTimeout()) return
                
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val pos = BlockPos(x, y, z)
                        try {
                            val state = physicsLevel.mcLevel.getBlockState(pos)
                            if (!state.isAir && state.isSolid) {
                                blocks.add(pos)
                            }
                        } catch (e: Exception) {
                            SparkCore.LOGGER.debug("获取方块状态失败: $pos, ${e.message}")
                        }
                    }
                }
            }
            
            SparkCore.LOGGER.info("三维区块 $chunkPos3D 收集到 ${blocks.size} 个实体方块")
            
            if (blocks.isEmpty()) {
                SparkCore.LOGGER.info("三维区块 $chunkPos3D 没有实体方块，跳过合并")
                return
            }
            
            // 检查是否超时
            if (shouldTimeout()) return
            
            // 按照空间邻近性对方块进行分组
            val blockGroups = groupBlocksByAdjacency(blocks)
            
            SparkCore.LOGGER.info("三维区块 $chunkPos3D 分组后有 ${blockGroups.size} 个方块组")
            
            // 处理每个方块组
            var mergedGroupsCount = 0
            
            blockGroups.forEach { group ->
                // 再次检查是否超时
                if (shouldTimeout()) return@forEach
                
                if (group.size < config.minMergeSize) {
                    SparkCore.LOGGER.debug("方块组大小(${group.size})小于最小合并尺寸(${config.minMergeSize})，跳过")
                    return@forEach
                }
                
                try {
                    // 创建合并后的碰撞体
                    createMergedBody(group)
                    mergedGroupsCount++
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("合并方块组时出错", e)
                }
            }
            
            val endTime = System.nanoTime()
            val duration = (endTime - startTime) / 1_000_000.0
            
            SparkCore.LOGGER.info("三维区块 $chunkPos3D 合并完成，处理了 $mergedGroupsCount/${blockGroups.size} 个方块组，耗时 $duration ms")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("合并三维区块时出错", e)
        } finally {
            releaseLock(lockKey)
        }
    }
}

/**
 * 边界框数据类
 */
data class BoundingBox(
    val min: Vector3f,
    val max: Vector3f
) 