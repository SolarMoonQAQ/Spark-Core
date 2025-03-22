package cn.solarmoon.spark_core.physics.terrain

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import kotlin.math.floor

/**
 * 三维地形区块坐标
 * 扩展了原始的MC区块坐标系统，添加了高度维度
 * 将垂直空间划分为多个子区块
 */
data class TerrainChunkPos3D(val x: Int, val y: Int, val z: Int) {
    
    companion object {
        // 垂直区块的高度（方块数）
        const val VERTICAL_CHUNK_HEIGHT = 16
        
        // 世界高度范围（针对1.18+版本）
        const val MIN_WORLD_HEIGHT = -64
        const val MAX_WORLD_HEIGHT = 320
        
        // 计算垂直区块数量
        const val VERTICAL_CHUNKS_COUNT = (MAX_WORLD_HEIGHT - MIN_WORLD_HEIGHT) / VERTICAL_CHUNK_HEIGHT
        
        /**
         * 根据方块Y坐标计算对应的垂直区块索引
         */
        fun getYIndex(blockY: Int): Int {
            return ((blockY - MIN_WORLD_HEIGHT) / VERTICAL_CHUNK_HEIGHT).coerceIn(0, VERTICAL_CHUNKS_COUNT - 1)
        }
        
        /**
         * 从实体位置创建三维区块坐标
         */
        fun fromEntity(entity: Entity): TerrainChunkPos3D {
            val blockX = entity.blockPosition().x
            val blockY = entity.blockPosition().y
            val blockZ = entity.blockPosition().z
            
            return fromBlockCoordinates(blockX, blockY, blockZ)
        }
        
        /**
         * 从方块坐标创建三维区块坐标
         */
        fun fromBlockCoordinates(blockX: Int, blockY: Int, blockZ: Int): TerrainChunkPos3D {
            val chunkX = blockX shr 4
            val chunkZ = blockZ shr 4
            val chunkY = (blockY - MIN_WORLD_HEIGHT) / VERTICAL_CHUNK_HEIGHT
            
            return TerrainChunkPos3D(chunkX, chunkY, chunkZ)
        }
        
        /**
         * 从原始MC区块坐标和高度创建三维区块坐标
         */
        @Suppress("UNUSED")
        fun fromChunkPos(chunkPos: ChunkPos, blockY: Int): TerrainChunkPos3D {
            val chunkY = (blockY - MIN_WORLD_HEIGHT) / VERTICAL_CHUNK_HEIGHT
            return TerrainChunkPos3D(chunkPos.x, chunkY, chunkPos.z)
        }
    }
    
    /**
     * 获取该三维区块的原始MC区块坐标
     */
    fun toChunkPos(): ChunkPos {
        return ChunkPos(x, z)
    }
    
    /**
     * 获取该三维区块覆盖的方块Y坐标范围
     */
    fun getYRange(): IntRange {
        val minY = MIN_WORLD_HEIGHT + y * VERTICAL_CHUNK_HEIGHT
        val maxY = minY + VERTICAL_CHUNK_HEIGHT - 1
        return minY..maxY
    }
    
    /**
     * 判断给定的方块坐标是否位于该三维区块内
     */
    @Suppress("UNUSED")
    fun containsBlock(blockX: Int, blockY: Int, blockZ: Int): Boolean {
        val chunkX = blockX shr 4
        val chunkZ = blockZ shr 4
        val yRange = getYRange()
        
        return chunkX == this.x && chunkZ == this.z && blockY in yRange
    }
    
    /**
     * 计算到另一个三维区块的曼哈顿距离
     */
    @Suppress("UNUSED")
    fun distanceTo(other: TerrainChunkPos3D): Int {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z)
    }
    
    /**
     * 获取所有邻接的三维区块坐标
     */
    @Suppress("UNUSED")
    fun getNeighbors(): List<TerrainChunkPos3D> {
        val neighbors = mutableListOf<TerrainChunkPos3D>()
        
        // 添加水平面的8个邻居
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                neighbors.add(TerrainChunkPos3D(x + dx, y, z + dz))
            }
        }
        
        // 添加上下两个邻居
        if (y > 0) neighbors.add(TerrainChunkPos3D(x, y - 1, z))
        if (y < VERTICAL_CHUNKS_COUNT - 1) neighbors.add(TerrainChunkPos3D(x, y + 1, z))
        
        return neighbors
    }
    
    override fun toString(): String {
        return "TerrainChunkPos3D(x=$x, y=$y, z=$z)"
    }
} 