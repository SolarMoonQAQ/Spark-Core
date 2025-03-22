package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 三维地形区块缓存管理器
 * 负责管理三维区块、缓存状态和物理碰撞体的生命周期
 */
class TerrainChunkCache(private val physicsLevel: PhysicsLevel) {
    
    // 区块状态枚举
    enum class ChunkState {
        PENDING,    // 等待加载/处理
        LOADING,    // 正在加载
        READY,      // 已加载且活跃
        INACTIVE,   // 不活跃（无玩家附近）
        EXPIRED     // 已过期，等待卸载
    }
    
    // 区块数据类
    data class ChunkData(
        val pos: TerrainChunkPos3D,
        var state: ChunkState = ChunkState.PENDING,
        val blocks: MutableSet<BlockPos> = mutableSetOf(),
        val collisionBodies: MutableSet<PhysicsRigidBody> = mutableSetOf(),
        var lastAccessTime: Long = System.currentTimeMillis(),
        var expirationTimer: Int = 0, // 过期倒计时（以tick为单位）
        var retryCount: Int = 0 // 重试次数
    )
    
    // 维护一个3D区块缓存映射表
    private val chunkCache = ConcurrentHashMap<TerrainChunkPos3D, ChunkData>()
    
    // 处理队列 - 按优先级排序待处理的区块
    private val processingQueue = ConcurrentLinkedQueue<TerrainChunkPos3D>()
    
    // 活跃区块集合 - 当前正在被玩家使用的区块
    private val activeChunks = ConcurrentHashMap.newKeySet<TerrainChunkPos3D>()
    
    // 区块到实体的映射，记录哪些实体关联了哪些区块
    private val entityChunkMap = ConcurrentHashMap<Entity, MutableSet<TerrainChunkPos3D>>()
    
    // 配置参数
    private var expirationTime = 600 // 区块过期时间（tick）
    private var maxCachedChunks = 4096 // 最大缓存区块数
    private var maxProcessingChunks = 4 // 每tick最大处理区块数
    private var MAX_RETRY_COUNT = 5 // 最大重试次数
    private var BASE_RETRY_DELAY_MS = 100L // 基础重试延迟时间（毫秒）
    
    // 获取活跃的实体列表（通常是玩家）
    private fun getActiveEntities(): List<Entity> {
        return physicsLevel.mcLevel.players()
    }
    
    /**
     * 更新实体周围的区块加载
     * 以实体为中心，确保周围3x3x3（共27个）区块被加载
     */
    fun updateEntityChunks(entity: Entity) {
        val centerPos = TerrainChunkPos3D.fromEntity(entity)
        
        // 记录实体之前占用的区块
        val oldChunks = entityChunkMap.getOrPut(entity) { ConcurrentHashMap.newKeySet() }
        val newChunks = ConcurrentHashMap.newKeySet<TerrainChunkPos3D>()
        
        // 计算实体新的3x3x3区块范围
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val chunkPos = TerrainChunkPos3D(centerPos.x + dx, centerPos.y + dy, centerPos.z + dz)
                    newChunks.add(chunkPos)
                    ensureChunkLoaded(chunkPos, true)
                }
            }
        }
        
        // 找出不再需要的区块
        val chunksToRelease = oldChunks.filter { it !in newChunks }
        
        // 更新活跃区块集合
        activeChunks.removeAll(chunksToRelease)
        activeChunks.addAll(newChunks)
        
        // 更新实体-区块映射
        entityChunkMap[entity] = newChunks
        
        // 处理不再需要的区块
        chunksToRelease.forEach { chunk ->
            // 检查是否有其他实体仍在使用该区块
            val isStillActive = entityChunkMap.values.any { chunk in it }
            
            if (!isStillActive) {
                // 如果没有实体使用，标记为非活跃
                chunkCache[chunk]?.apply {
                    state = ChunkState.INACTIVE
                    expirationTimer = expirationTime
                }
            }
        }
    }
    
    /**
     * 确保指定的区块被加载
     * @param pos 三维区块坐标
     * @param highPriority 是否高优先级（高优先级区块会被优先处理）
     */
    fun ensureChunkLoaded(pos: TerrainChunkPos3D, highPriority: Boolean = false) {
        // 如果区块已经在缓存中且状态是READY，仅更新访问时间
        val existingData = chunkCache[pos]
        
        if (existingData != null) {
            existingData.lastAccessTime = System.currentTimeMillis()
            
            // 如果区块已过期或不活跃，重新激活它
            if (existingData.state == ChunkState.EXPIRED || existingData.state == ChunkState.INACTIVE) {
                existingData.state = ChunkState.READY
                existingData.expirationTimer = 0
                activeChunks.add(pos)
            }
            
            return
        }
        
        // 创建新的区块数据
        val chunkData = ChunkData(pos, ChunkState.PENDING)
        chunkCache[pos] = chunkData
        
        // 添加到处理队列
        if (highPriority) {
            processingQueue.add(pos) // 高优先级添加到队列前部
        } else {
            processingQueue.add(pos)
        }
    }
    
    /**
     * 处理区块加载队列
     * 每帧处理有限数量的区块，避免卡顿
     */
    fun processQueue() {
        var processed = 0
        val startTime = System.currentTimeMillis()
        val timeoutMillis = 100L // 100ms超时限制
        
        while (processed < maxProcessingChunks && processingQueue.isNotEmpty()) {
            // 检查超时
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                SparkCore.LOGGER.warn("区块处理队列执行超时，已处理 $processed 个区块")
                break
            }
            
            val pos = processingQueue.poll() ?: break
            
            try {
                val chunkData = chunkCache[pos]
                
                if (chunkData == null) {
                    SparkCore.LOGGER.warn("从队列中获取的区块 $pos 不在缓存中")
                    continue
                }
                
                // 只处理PENDING状态的区块
                if (chunkData.state == ChunkState.PENDING) {
                    SparkCore.LOGGER.debug("处理PENDING状态的区块: $pos")
                    chunkData.state = ChunkState.LOADING
                    loadChunk(pos)
                    processed++
                } else {
                    SparkCore.LOGGER.debug("跳过非PENDING状态的区块: $pos, 当前状态: ${chunkData.state}")
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("处理区块队列时出错: $pos", e)
                // 出错的区块重新加入队列末尾，以便稍后重试
                processingQueue.add(pos)
            }
        }
        
        if (processed > 0) {
            SparkCore.LOGGER.debug("处理了 $processed 个区块，队列剩余: ${processingQueue.size}")
        }
    }
    
    /**
     * 加载区块数据并创建碰撞体
     */
    private fun loadChunk(pos: TerrainChunkPos3D) {
        val startTime = System.currentTimeMillis()
        val chunkData = chunkCache[pos] ?: return
        SparkCore.LOGGER.debug("开始加载三维区块: $pos")
        
        // 获取原始MC区块
        val mcChunkPos = pos.toChunkPos()
        val mcChunk = physicsLevel.terrainChunks[mcChunkPos]
        
        if (mcChunk == null) {
            // 增加重试计数
            chunkData.retryCount = (chunkData.retryCount ?: 0) + 1
            
            // 检查是否超过最大重试次数
            if (chunkData.retryCount > MAX_RETRY_COUNT) {
                SparkCore.LOGGER.warn("三维区块 $pos 重试次数过多(${chunkData.retryCount}/$MAX_RETRY_COUNT)，标记为EXPIRED")
                chunkData.state = ChunkState.EXPIRED
                return
            }
            
            SparkCore.LOGGER.warn("无法找到MC区块: $mcChunkPos, 可能尚未加载或已卸载 (重试 ${chunkData.retryCount}/$MAX_RETRY_COUNT)")
            
            // 重新标记为PENDING，下次再尝试加载
            chunkData.state = ChunkState.PENDING
            
            // 计算重试延迟时间，随着重试次数增加而增加延迟
            val delayFactor = minOf(chunkData.retryCount, 10) // 最多延迟10倍
            
            // 延迟重试，避免过于频繁地访问未加载的区块
            Thread {
                try {
                    // 延迟一段时间后再加入处理队列，避免频繁空轮询
                    Thread.sleep(BASE_RETRY_DELAY_MS * delayFactor)
                    processingQueue.add(pos)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("延迟重试加载区块失败", e)
                }
            }.apply {
                name = "Chunk-Load-Retry-Thread"
                isDaemon = true
                start()
            }
            return
        }
        
        // 重置重试计数
        chunkData.retryCount = 0
        
        try {
            // 获取区块的Y坐标范围
            val yRange = pos.getYRange()
            SparkCore.LOGGER.debug("区块 $pos 的Y范围: $yRange")
            
            // 收集实体方块
            val beforeBlockCount = chunkData.blocks.size
            collectSolidBlocks(pos, chunkData, yRange)
            SparkCore.LOGGER.debug("区块 $pos 收集了 ${chunkData.blocks.size} 个方块 (新增: ${chunkData.blocks.size - beforeBlockCount})")
            
            if (chunkData.blocks.isEmpty()) {
                SparkCore.LOGGER.debug("区块 $pos 没有实体方块，标记为READY状态")
                chunkData.state = ChunkState.READY
                return
            }
            
            // 使用互斥锁以防止重复处理相同区块
            val lockKey = "chunk_lock_$pos"
            val isLocked = !physicsLevel.terrainManager.mergeManager.acquireLock(lockKey)
            
            if (isLocked) {
                SparkCore.LOGGER.warn("区块 $pos 正在被其他线程处理，稍后重试")
                chunkData.state = ChunkState.PENDING
                processingQueue.add(pos)
                return
            }
            
            // 触发区块合并逻辑 - 使用并行处理
            physicsLevel.submitTask {
                try {
                    // 触发区块合并
                    physicsLevel.terrainManager.mergeManager.mergeChunk3D(pos)
                    chunkData.state = ChunkState.READY
                    val duration = System.currentTimeMillis() - startTime
                    SparkCore.LOGGER.info("三维区块加载完成: $pos, 包含 ${chunkData.blocks.size} 个方块, 耗时 $duration ms")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("合并三维区块时出错: $pos", e)
                    chunkData.state = ChunkState.PENDING  // 标记为待处理，以便稍后重试
                    // 重新加入队列
                    processingQueue.add(pos)
                } finally {
                    // 释放锁
                    physicsLevel.terrainManager.mergeManager.releaseLock(lockKey)
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("加载三维区块时出错: $pos", e)
            chunkData.state = ChunkState.PENDING  // 标记为待处理，以便稍后重试
            // 出错后重新加入队列
            processingQueue.add(pos)
        }
    }
    
    /**
     * 收集指定区块和高度范围内的实体方块
     */
    private fun collectSolidBlocks(pos: TerrainChunkPos3D, chunkData: ChunkData, yRange: IntRange) {
        val mcChunk = physicsLevel.terrainChunks[pos.toChunkPos()] ?: return
        
        for (x in 0..15) {
            for (y in yRange) {
                for (z in 0..15) {
                    val blockPos = BlockPos(
                        pos.x * 16 + x,
                        y,
                        pos.z * 16 + z
                    )
                    
                    try {
                        val state = mcChunk.getBlockState(blockPos)
                        if (state.isSolid) {
                            chunkData.blocks.add(blockPos)
                            physicsLevel.terrainManager.addTerrainElement(blockPos, state)
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("获取方块状态失败: $blockPos, ${e.message}")
                    }
                }
            }
        }
        
        SparkCore.LOGGER.debug("三维区块 $pos 收集了 ${chunkData.blocks.size} 个实体方块")
    }
    
    /**
     * 关联碰撞体到区块
     * 当碰撞体创建后，需要将它关联到对应的区块
     */
    fun associateBodyWithChunk(body: PhysicsRigidBody, blockPos: BlockPos) {
        val chunkPos = TerrainChunkPos3D.fromBlockCoordinates(blockPos.x, blockPos.y, blockPos.z)
        val chunkData = chunkCache[chunkPos]
        
        if (chunkData != null) {
            chunkData.collisionBodies.add(body)
            // 设置过期倒计时，确保物理体在区块卸载时能够被正确清理
            body.setUserIndex(expirationTime)
        }
    }
    
    /**
     * 更新区块状态和过期计时器
     * 定期调用，处理区块和碰撞体的生命周期
     */
    fun update() {
        try {
            // 处理队列中的待加载区块
            processQueue()
            
            // 更新过期计时器
            val currentTime = System.currentTimeMillis()
            val failedChunks = mutableListOf<TerrainChunkPos3D>()

            //60 秒一次
            // 记录当前状态统计
            val pendingCount = chunkCache.values.count { it.state == ChunkState.PENDING }
            val loadingCount = chunkCache.values.count { it.state == ChunkState.LOADING }
            val readyCount = chunkCache.values.count { it.state == ChunkState.READY }
            val inactiveCount = chunkCache.values.count { it.state == ChunkState.INACTIVE }
            val expiredCount = chunkCache.values.count { it.state == ChunkState.EXPIRED }
            if (currentTime % 60000 == 0L){
                SparkCore.LOGGER.debug("三维区块状态统计: PENDING=$pendingCount, LOADING=$loadingCount, READY=$readyCount, INACTIVE=$inactiveCount, EXPIRED=$expiredCount, 队列大小=${processingQueue.size}")

            }

            chunkCache.forEach { (pos, data) ->
                try {
                    if (data.state == ChunkState.INACTIVE) {
                        // 对于不活跃的区块，减少过期计时器
                        data.expirationTimer--
                        
                        if (data.expirationTimer <= 0) {
                            // 过期时间结束，标记为已过期
                            data.state = ChunkState.EXPIRED
                            
                            // 尝试卸载区块的碰撞体
                            unloadChunkCollisionBodies(pos)
                        }
                    } else if (data.state == ChunkState.READY) {
                        // 检查长时间未访问的区块
                        val timeSinceLastAccess = currentTime - data.lastAccessTime
                        if (timeSinceLastAccess > 30000 && !activeChunks.contains(pos)) { // 30秒
                            // 标记为不活跃
                            data.state = ChunkState.INACTIVE
                            data.expirationTimer = expirationTime
                        }
                    } else if (data.state == ChunkState.PENDING) {
                        // 检查PENDING状态的区块是否在队列中，如果不在则重新加入
                        // 解决可能由于并发问题导致区块被遗漏的情况
                        if (data.retryCount < MAX_RETRY_COUNT && !processingQueue.contains(pos)) {
                            processingQueue.add(pos)
                        } else if (data.retryCount >= MAX_RETRY_COUNT) {
                            // 超过最大重试次数，将其标记为失败，稍后移除
                            failedChunks.add(pos)
                        }
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("更新区块状态时出错: $pos", e)
                    failedChunks.add(pos)
                }
            }
            
            // 清理失败的区块
            failedChunks.forEach { pos ->
                SparkCore.LOGGER.warn("移除失败的三维区块: $pos")
                chunkCache.remove(pos)
                activeChunks.remove(pos)
            }
            
            // 清理已过期的区块
            cleanupExpiredChunks()
            
            // 如果缓存过大，移除最旧的非活跃区块
            pruneCache()
        } catch (e: Exception) {
            SparkCore.LOGGER.error("区块缓存更新失败", e)
        }
    }
    
    /**
     * 卸载指定区块的所有碰撞体
     */
    private fun unloadChunkCollisionBodies(pos: TerrainChunkPos3D) {
        val chunkData = chunkCache[pos] ?: return
        
        SparkCore.LOGGER.debug("卸载三维区块碰撞体: $pos, 共${chunkData.collisionBodies.size}个")
        
        physicsLevel.submitTask {
            try {
                // 移除所有关联的碰撞体
                chunkData.collisionBodies.forEach { body ->
                    physicsLevel.world.removeCollisionObject(body)
                }
                chunkData.collisionBodies.clear()
            } catch (e: Exception) {
                SparkCore.LOGGER.error("卸载区块碰撞体时出错: $pos", e)
            }
        }
    }
    
    /**
     * 清理已过期的区块
     */
    private fun cleanupExpiredChunks() {
        val expiredChunks = chunkCache.entries
            .filter { it.value.state == ChunkState.EXPIRED }
            .map { it.key }
        
        expiredChunks.forEach { pos ->
            SparkCore.LOGGER.debug("移除过期三维区块: $pos")
            chunkCache.remove(pos)
            
            // 从活跃区块集合中移除
            activeChunks.remove(pos)
            
            // 清理区块内的方块数据
            val chunkData = chunkCache[pos]
            chunkData?.blocks?.forEach { blockPos ->
                physicsLevel.terrainManager.removeTerrainElement(blockPos)
            }
        }
    }
    
    /**
     * 缓存修剪，确保缓存大小不会超过限制
     */
    private fun pruneCache() {
        if (chunkCache.size <= maxCachedChunks) return
        
        // 找出最旧的非活跃区块
        val oldestInactiveChunks = chunkCache.entries
            .filter { it.value.state == ChunkState.INACTIVE }
            .sortedBy { it.value.lastAccessTime }
            .take(chunkCache.size - maxCachedChunks)
            .map { it.key }
        
        oldestInactiveChunks.forEach { pos ->
            SparkCore.LOGGER.debug("修剪缓存，移除旧区块: $pos")
            // 卸载区块碰撞体
            unloadChunkCollisionBodies(pos)
            // 从缓存中移除
            chunkCache.remove(pos)
        }
    }
    
    /**
     * 当实体移除时（如玩家登出），释放其占用的区块
     */
    fun releaseEntityChunks(entity: Entity) {
        val chunks = entityChunkMap.remove(entity) ?: return
        
        // 从活跃区块中移除不再被任何实体使用的区块
        chunks.forEach { chunk ->
            val isStillActive = entityChunkMap.values.any { chunk in it }
            if (!isStillActive) {
                activeChunks.remove(chunk)
                
                chunkCache[chunk]?.apply {
                    state = ChunkState.INACTIVE
                    expirationTimer = expirationTime
                }
            }
        }
    }
    
    /**
     * 处理一个方块状态变更
     * 找到对应的三维区块并更新
     */
    fun onBlockChanged(pos: BlockPos, newState: BlockState) {
        val chunkPos = TerrainChunkPos3D.fromBlockCoordinates(pos.x, pos.y, pos.z)
        val chunkData = chunkCache[chunkPos]
        
        if (chunkData != null) {
            // 移除旧的方块数据
            chunkData.blocks.remove(pos)
            
            // 为固体方块添加新数据
            if (!newState.isEmpty && !newState.isAir && newState.isSolid) {
                chunkData.blocks.add(pos)
                physicsLevel.terrainManager.addTerrainElement(pos, newState)
            } else {
                physicsLevel.terrainManager.removeTerrainElement(pos)
            }
            
            // 触发区块重新合并
            physicsLevel.submitTask {
                try {
                    physicsLevel.terrainManager.mergeManager.mergeChunk3D(chunkPos)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("重新合并区块时出错: $chunkPos", e)
                }
            }
        }
    }
    
    /**
     * 清空缓存（用于世界卸载或维度切换）
     */
    fun clear() {
        // 清理所有物理碰撞体
        val allChunks = ArrayList(chunkCache.keys)
        
        allChunks.forEach { pos ->
            unloadChunkCollisionBodies(pos)
        }
        
        // 清空所有集合
        chunkCache.clear()
        activeChunks.clear()
        entityChunkMap.clear()
        processingQueue.clear()
    }
    
    /**
     * 获取当前缓存状态的统计信息
     */
    fun getStats(): String {
        return """
            三维区块缓存统计:
            - 总缓存区块: ${chunkCache.size}
            - 活跃区块: ${activeChunks.size}
            - 待处理区块: ${processingQueue.size}
            - 实体关联区块映射: ${entityChunkMap.size}
            - 各状态区块数量:
              - PENDING: ${chunkCache.values.count { it.state == ChunkState.PENDING }}
              - LOADING: ${chunkCache.values.count { it.state == ChunkState.LOADING }}
              - READY: ${chunkCache.values.count { it.state == ChunkState.READY }}
              - INACTIVE: ${chunkCache.values.count { it.state == ChunkState.INACTIVE }}
              - EXPIRED: ${chunkCache.values.count { it.state == ChunkState.EXPIRED }}
        """.trimIndent()
    }
    
    /**
     * 释放指定的三维区块
     * 从缓存中移除该区块并清理相关资源
     */
    fun releaseChunk(pos: TerrainChunkPos3D) {
        val chunkData = chunkCache[pos]
        
        if (chunkData != null) {
            SparkCore.LOGGER.debug("释放三维区块: $pos")
            
            // 从活跃区块集合中移除
            activeChunks.remove(pos)
            
            // 卸载区块碰撞体
            unloadChunkCollisionBodies(pos)
            
            // 从缓存中移除
            chunkCache.remove(pos)
            
            // 清理区块内的方块数据
            chunkData.blocks.forEach { blockPos ->
                physicsLevel.terrainManager.removeTerrainElement(blockPos)
            }
        }
    }
} 