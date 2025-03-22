package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.event.PhysicsEventPayload
import cn.solarmoon.spark_core.physics.terrain.TerrainChunkPos3D

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PhysicsLevelApplier {
    
    // 性能报告周期（默认每分钟记录一次）
    private const val PERFORMANCE_REPORT_INTERVAL = 1200 // 60秒 * 20tps = 1200个tick
    private val lastReportTime = AtomicLong(0)
    
    // 玩家上次位置缓存
    private val playerLastPositions = ConcurrentHashMap<LivingEntity, BlockPos>()
    
    // 玩家移动触发更新的阈值距离（方块）
    private const val MOVEMENT_UPDATE_THRESHOLD = 2
    
    // 安全网更新周期（tick），为了确保系统稳定性，每300tick仍会执行一次更新
    private const val SAFETY_UPDATE_INTERVAL = 300

    @SubscribeEvent
    private fun load(event: LevelEvent.Load) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.load()
            // 初始化地形管理器
            level.physicsLevel.terrainManager.initialize()
        }
    }

    @SubscribeEvent
    private fun unLoad(event: LevelEvent.Unload) {
        val level = event.level
        if (level is Level) {
            level.physicsLevel.close()
        }
    }

    @SubscribeEvent
    private fun mcLevelTask(event: LevelTickEvent.Post) {
        val level = event.level
        level.physicsLevel.processTasks()
        level.physicsLevel.mcTick()
        
//        // 替代每10tick更新为安全网更新
//        if (level.gameTime % SAFETY_UPDATE_INTERVAL == 0L) {
//            SparkCore.LOGGER.debug("执行安全网三维地形更新（每${SAFETY_UPDATE_INTERVAL}tick）")
//
//            // 使用异步方式调用updateTerrain3D，避免在主线程中执行过长时间
//            Thread {
//                try {
//                    level.physicsLevel.terrainManager.updateTerrain3D()
//                } catch (e: Exception) {
//                    SparkCore.LOGGER.error("异步执行三维地形更新时发生错误", e)
//                }
//            }.apply {
//                name = "Terrain3D-Update-Thread"
//                isDaemon = true
//                start()
//            }
//        }
    }
    
    /**
     * 处理玩家移动事件
     * 使用EnteringSection事件直接检测玩家是否跨区块
     */
    @SubscribeEvent
    fun onPlayerMove(event: EntityEvent.EnteringSection) {
        if (event.entity !is Player) return
        val player = event.entity
        val level = player.level()
        
//        if (level.isClientSide) return // 仅在服务端处理
        
        // 使用事件提供的didChunkChange()方法直接检测是否跨区块
        val shouldUpdate = event.didChunkChange() || playerLastPositions.get(player as LivingEntity) == null
        
        if (shouldUpdate) {
            // 更新玩家位置缓存
            playerLastPositions.put(player as LivingEntity, player.blockPosition())
            
            SparkCore.LOGGER.debug("玩家 ${player.name} 跨区块移动，触发三维区块系统更新")
            
            // 向客户端发送玩家移动事件数据包
            if (!level.isClientSide) {
                PacketDistributor.sendToAllPlayers(
                    PhysicsEventPayload(PhysicsEventPayload.EventType.PLAYER_MOVE, player.blockPosition())
                )
            }
            
            // 使用异步线程处理三维区块更新
            Thread {
                try {
                    level.physicsLevel.terrainManager.mergePlayerChunk(player)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("异步执行三维区块更新时发生错误", e)
                }
            }.apply {
                name = "Player-Chunk3D-Update-Thread"
                isDaemon = true
                start()
            }
        }
    }
    
    /**
     * 处理玩家登录事件，初始化玩家周围的区块
     */
    @SubscribeEvent
    fun playerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        val level = player.level()

        // 初始化玩家周围的三维区块
        Thread {
            try {
                level.physicsLevel.terrainManager.mergePlayerChunk(player)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("初始化玩家周围三维区块时发生错误", e)
            }
        }.apply {
            name = "Player-Login-Chunk3D-Init"
            isDaemon = true
            start()
        }
    }
    
    /**
     * 处理玩家登出事件，释放玩家关联的区块
     */
    @SubscribeEvent
    fun playerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity
        val level = player.level()
        
        // 清除位置缓存
        playerLastPositions.remove(player as LivingEntity)
        
        // 释放玩家关联的三维区块
        level.physicsLevel.terrainManager.releasePlayerChunks(player)
    }
    
    /**
     * 记录性能报告
     */
    @SubscribeEvent
    private fun serverTick(event: ServerTickEvent.Post) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastReportTime.get()

        // 每隔一段时间记录一次性能报告
        if (currentTime - lastTime > PERFORMANCE_REPORT_INTERVAL * 50) { // 转换为毫秒
            lastReportTime.set(currentTime)

            // 遍历所有维度记录性能信息
            event.server.allLevels.forEach { level ->
                val report = level.physicsLevel.getPerformanceReport()
                SparkCore.LOGGER.info("维度 [${level.dimension().location()}] 物理引擎性能报告:\n$report")
            }
        }
    }

    /**
     * 区块加载事件 - 触发地形更新
     */
    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val chunk = event.chunk
        val level = event.level

        if (level is Level && chunk is ChunkAccess) {
            val chunkPos = chunk.pos

            // 更新区块缓存
            level.physicsLevel.terrainChunks[chunkPos] = chunk

            // 向客户端发送区块加载事件数据包
            if (!level.isClientSide) {
                PacketDistributor.sendToAllPlayers(
                    PhysicsEventPayload(PhysicsEventPayload.EventType.CHUNK_LOAD, chunkPos)
                )
            }

            // 检查是否有玩家在附近
            val minX = chunkPos.x * 16
            val minZ = chunkPos.z * 16
            val nearestPlayer = level.getNearestPlayer(
                minX + 8.0,
                0.0,
                minZ + 8.0,
                64.0, // 只考虑64格范围内的玩家
                null
            )

            if (nearestPlayer != null) {
                SparkCore.LOGGER.debug("区块加载时有玩家在附近，触发三维区块更新, ChunkPos: $chunkPos")

                // 使用异步线程处理
                Thread {
                    try {
//                        // 获取玩家高度对应的三维区块
//                        val playerY = nearestPlayer.blockPosition().y
//                        val chunk3DY = TerrainChunkPos3D.getYIndex(playerY)
//
//                        // 对玩家所在高度的三维区块进行处理
//                        val chunk3D = TerrainChunkPos3D(chunkPos.x, chunk3DY, chunkPos.z)
//                        level.physicsLevel.terrainManager.mergeManager.mergeChunk3D(chunk3D)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("区块加载时更新三维区块发生错误", e)
                    }
                }.apply {
                    name = "Chunk-Load-3D-Update-Thread"
                    isDaemon = true
                    start()
                }
            }
        }
    }
    
    /**
     * 区块卸载时从physicsLevel缓存中移除
     */
    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level
        if (level is Level) {
            val chunkPosition = event.chunk.pos
            SparkCore.LOGGER.debug("区块卸载，原始pos类型: ${chunkPosition.javaClass.name}, 值: $chunkPosition")
            
            // 确保使用ChunkPos类型作为键
            val chunkPos = if (chunkPosition is ChunkPos) {
                chunkPosition
            } else {
                // 如果不是ChunkPos类型，则创建一个ChunkPos对象
                val coordinates = chunkPosition.toString().split(",")
                if (coordinates.size >= 2) {
                    try {
                        val x = coordinates[0].trim().toInt()
                        val z = coordinates[1].trim().toInt()
                        ChunkPos(x, z)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("无法解析区块坐标: $chunkPosition, ${e.message}")
                        return
                    }
                } else {
                    SparkCore.LOGGER.error("无法解析区块坐标: $chunkPosition")
                    return
                }
            }
            
            // 向客户端发送区块卸载事件数据包
            if (!level.isClientSide) {
                PacketDistributor.sendToAllPlayers(
                    PhysicsEventPayload(PhysicsEventPayload.EventType.CHUNK_UNLOAD, chunkPos)
                )
            }
            
            // 从physicsLevel缓存中移除区块
            val removed = level.physicsLevel.terrainChunks.remove(chunkPos)
            
            // 同时从三维区块缓存中移除所有相关的三维区块
            if (removed != null) {
                // 遍历所有可能的Y轴索引
                for (y in 0 until TerrainChunkPos3D.VERTICAL_CHUNKS_COUNT) {
                    val chunk3D = TerrainChunkPos3D(chunkPos.x, y, chunkPos.z)
                    level.physicsLevel.terrainManager.chunkCache.releaseChunk(chunk3D)
                }
            }
            
            SparkCore.LOGGER.debug("区块已从缓存中移除: $chunkPos, 移除结果: ${removed != null}")
        }
    }
    
    /**
     * 方块破坏事件 - 触发地形更新
     */
    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val level = event.level
        if (level is Level) {
            val blockPos = event.pos
            
            // 获取距离方块最近的玩家
            val nearestPlayer = level.getNearestPlayer(
                blockPos.x.toDouble(), 
                blockPos.y.toDouble(), 
                blockPos.z.toDouble(), 
                64.0, // 只考虑64格范围内的玩家
                null
            )
            
            // 只有当有玩家在附近时才执行更新
            if (nearestPlayer != null) {
                SparkCore.LOGGER.debug("方块破坏，玩家${nearestPlayer.name.string}在附近，触发三维区块更新")
                
                // 向客户端发送方块破坏事件数据包
                if (!level.isClientSide) {
                    PacketDistributor.sendToAllPlayers(
                        PhysicsEventPayload(PhysicsEventPayload.EventType.BLOCK_BREAK, blockPos, event.state)
                    )
                }
                
                // 使用异步线程处理
                Thread {
                    try {
                        // 处理方块状态变更
                        level.physicsLevel.terrainManager.onBlockChanged(blockPos, event.state)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("方块破坏时更新三维区块发生错误", e)
                    }
                }.apply {
                    name = "Block-Break-3D-Update-Thread"
                    isDaemon = true
                    start()
                }
            } else {
                SparkCore.LOGGER.debug("方块破坏，但附近没有玩家，跳过三维区块更新")
            }
        }
    }
    
    /**
     * 方块放置时触发地形更新
     */
    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level
        if (level is Level) {
            val blockPos = event.pos
            
            // 检查放置方块的实体是否是玩家
            val entity = event.entity
            val isPlayerNearby = if (entity is Player) {
                // 如果是玩家放置的方块，直接使用该玩家
                true
            } else {
                // 否则，查找附近的玩家
                val nearestPlayer = level.getNearestPlayer(
                    blockPos.x.toDouble(), 
                    blockPos.y.toDouble(), 
                    blockPos.z.toDouble(), 
                    64.0, // 只考虑64格范围内的玩家
                    null
                )
                nearestPlayer != null
            }
            
            // 只有当有玩家在附近时才执行更新
            if (isPlayerNearby) {
                SparkCore.LOGGER.debug("方块放置，玩家在附近，触发三维区块更新: $blockPos")
                
                // 向客户端发送方块放置事件数据包
                if (!level.isClientSide) {
                    PacketDistributor.sendToAllPlayers(
                        PhysicsEventPayload(PhysicsEventPayload.EventType.BLOCK_PLACE, blockPos, event.state)
                    )
                }
                
                // 使用异步线程处理
                Thread {
                    try {
                        // 处理方块状态变更
                        level.physicsLevel.terrainManager.onBlockChanged(blockPos, event.state)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("方块放置时更新三维区块发生错误", e)
                    }
                }.apply {
                    name = "Block-Place-3D-Update-Thread"
                    isDaemon = true
                    start()
                }
            } else {
                SparkCore.LOGGER.debug("方块放置，但附近没有玩家，跳过三维区块更新")
            }
        }
    }
}