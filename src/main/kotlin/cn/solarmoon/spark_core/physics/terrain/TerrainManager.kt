package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.terrain.merge.TerrainMergeManager
import cn.solarmoon.spark_core.physics.terrain.config.TerrainMergeConfig
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.collections.joinToString
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 地形管理器
 * 负责管理和优化地形碰撞体的生成和更新
 */
class TerrainManager(private val physicsLevel: PhysicsLevel) {
    // 已加载的地形块
    private val loadedChunks = ConcurrentHashMap<ChunkPos, TerrainChunk>()
    
    // 地形元素缓存
    private val terrainElements = ConcurrentHashMap<BlockPos, TerrainElement>()
    
    // 合并管理器
    internal val mergeManager = TerrainMergeManager(physicsLevel)

    // 三维区块缓存管理器
    internal val chunkCache = TerrainChunkCache(physicsLevel)

    /**
     * 初始化地形管理器
     */
    fun initialize() {
        SparkCore.LOGGER.info("初始化地形管理器")
        // 启动一个后台线程以定期更新缓存状态
        Thread {
            try {
                while (true) {
                    chunkCache.update()
                    Thread.sleep(1000) // 每秒更新一次
                }
            } catch (e: InterruptedException) {
                SparkCore.LOGGER.info("地形缓存更新线程已停止")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("地形缓存更新发生错误", e)
            }
        }.apply {
            name = "Terrain-Cache-Updater"
            isDaemon = true
            start()
        }
    }

    /**
     * 基于三维区块系统更新地形
     * 为每个玩家加载周围的区块
     */
    fun updateTerrain3D() {
        val players = physicsLevel.mcLevel.players()
        if (players.isEmpty()) return
        
        SparkCore.LOGGER.debug("更新三维地形，玩家数量: ${players.size}")
        
        // 更新每个玩家周围的区块
        players.forEach { player ->
            try {
                chunkCache.updateEntityChunks(player)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("更新玩家周围区块时出错", e)
            }
        }
    }

    /**
     * 基于三维区块系统更新指定实体周围的地形
     * @param entity 要更新周围地形的实体（通常是玩家）
     */
    fun updateTerrain3D(entity: Entity) {
        SparkCore.LOGGER.debug("更新实体(${entity.name.string})周围的三维地形")
        try {
            chunkCache.updateEntityChunks(entity)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("更新实体周围区块时出错", e)
        }
    }

    /**
     * 添加地形元素
     */
    fun addTerrainElement(pos: BlockPos, state: BlockState) {
        terrainElements[pos] = TerrainElement(state, pos)
    }

    /**
     * 移除地形元素
     */
    fun removeTerrainElement(pos: BlockPos) {
        terrainElements.remove(pos)
        mergeManager.removeMergedBody(pos)
    }

    /**
     * 处理方块状态变化
     * 使用三维区块系统
     */
    fun onBlockChanged(pos: BlockPos, newState: BlockState) {
        SparkCore.LOGGER.info("方块变更事件: $pos")
        
        // 移除旧的合并碰撞体
        mergeManager.removeMergedBody(pos)
        
        // 使用三维区块系统处理方块变更
        chunkCache.onBlockChanged(pos, newState)
    }

    /**
     * 合并玩家所在的三维区块
     * 使用新的三维区块系统
     */
    fun mergePlayerChunk(entity: Entity) {
        SparkCore.LOGGER.info("合并玩家(${entity.name.string})所在三维区块的地形")
        
        // 获取玩家所在的三维区块
        val centerChunk3D = TerrainChunkPos3D.fromEntity(entity)
        
        // 确保该区块被加载
        chunkCache.ensureChunkLoaded(centerChunk3D, true)
        
        // 通知缓存系统更新玩家周围的区块
        chunkCache.updateEntityChunks(entity)
    }

    /**
     * 玩家登出或切换维度时，释放关联的区块
     */
    fun releasePlayerChunks(player: Entity) {
        chunkCache.releaseEntityChunks(player)
    }

    /**
     * 清理地形管理器资源
     */
    fun cleanup() {
        loadedChunks.clear()
        terrainElements.clear()
        chunkCache.clear()
    }

    /**
     * 获取性能报告
     * 包含三维区块缓存的统计信息
     */
    fun getPerformanceReport(): String {
        return """
            地形系统性能报告:
            已加载区块数: ${loadedChunks.size}
            地形元素数量: ${terrainElements.size}
            ${mergeManager.getPerformanceReport()}
            
            三维区块缓存:
            ${chunkCache.getStats()}
        """.trimIndent()
    }

    /**
     * 获取最近的玩家位置
     */
    private fun getNearestPlayerPos(): BlockPos {
        val player = physicsLevel.mcLevel.getNearestPlayer(0.0, 0.0, 0.0, Double.MAX_VALUE, null)
        return player?.blockPosition() ?: BlockPos(0, 0, 0)
    }

    
    /**
     * 卸载过远的地形块
     */
    private fun unloadDistantChunks(center: BlockPos, maxDistance: Int) {
        // 将玩家位置转换为区块坐标
        val playerChunkX = center.x shr 4
        val playerChunkZ = center.z shr 4
        val playerChunkPos = ChunkPos(playerChunkX, playerChunkZ)
        
        loadedChunks.entries.removeIf { (pos, chunk) ->
            if (calculateChunkDistance(pos, playerChunkPos) > maxDistance) {
                unloadChunk(pos)
                true
            } else false
        }
    }
    
    /**
     * 卸载地形块
     */
    private fun unloadChunk(pos: ChunkPos) {
        val chunk = loadedChunks.remove(pos) ?: return
        
        // 移除地形元素
        chunk.blocks.forEach { blockPos ->
            terrainElements.remove(blockPos)
            mergeManager.removeMergedBody(blockPos)
        }

    }
    
    /**
     * 从缓存中获取区块，考虑可能的键类型不匹配问题
     */
    private fun getChunkFromCache(chunkPos: ChunkPos): ChunkAccess? {
        // 直接尝试从缓存中获取
        val directLookup = physicsLevel.terrainChunks[chunkPos]
        if (directLookup != null) {
            SparkCore.LOGGER.debug("直接从缓存中找到区块 $chunkPos")
            return directLookup
        }
        
        // 如果直接查找失败，尝试通过坐标匹配
        for ((key, chunk) in physicsLevel.terrainChunks) {
            if (key.x == chunkPos.x && key.z == chunkPos.z) {
                SparkCore.LOGGER.debug("通过坐标匹配找到区块，键类型: ${key.javaClass.name}")
                // 由于找到的是正确的区块，可以更新缓存以便下次直接获取
                physicsLevel.terrainChunks.remove(key)
                physicsLevel.terrainChunks[chunkPos] = chunk
                return chunk
            }
        }
        
        SparkCore.LOGGER.debug("在缓存中找不到区块 $chunkPos")
        return null
    }

    
    /**
     * 计算区块到另一个区块的距离
     */
    private fun calculateChunkDistance(pos1: ChunkPos, pos2: ChunkPos): Int {
        val dx = pos1.x - pos2.x
        val dz = pos1.z - pos2.z
        return sqrt((dx * dx + dz * dz).toDouble()).toInt()
    }
    
    /**
     * 计算区块到玩家的距离
     */
    private fun calculateChunkToPlayerDistance(chunkPos: ChunkPos, playerPos: BlockPos): Int {
        // 将玩家方块坐标转换为区块坐标
        val playerChunkX = playerPos.x shr 4 // 相当于除以16
        val playerChunkZ = playerPos.z shr 4
        
        val dx = chunkPos.x - playerChunkX
        val dz = chunkPos.z - playerChunkZ
        return sqrt((dx * dx + dz * dz).toDouble()).toInt()
    }
    
    /**
     * 获取周围的方块
     */
    private fun getSurroundingBlocks(pos: BlockPos): List<BlockPos> {
        return listOf(
            pos.above(),
            pos.below(),
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west()
        ).filter { terrainElements.containsKey(it) }
    }
    
    /**
     * 清理过期的地形碰撞体
     */
    fun clearExpiredBodies() {
        // 清理过期的地形元素
        terrainElements.entries.removeIf { (pos, element) ->
            if (element.isExpired) {
                mergeManager.removeMergedBody(pos)
                true
            } else false
        }
        //TODO: 适配新生成条件
    }

}

/**
 * 地形区块 - 表示一个16×16的区块
 */
data class TerrainChunk(
    val centerPos: ChunkPos,
    val blocks: MutableSet<BlockPos> = mutableSetOf()
)

/**
 * 地形元素 - 代表单个方块的碰撞信息
 */
data class TerrainElement(
    val state: BlockState,
    val pos: BlockPos,
    var isExpired: Boolean = false
) 