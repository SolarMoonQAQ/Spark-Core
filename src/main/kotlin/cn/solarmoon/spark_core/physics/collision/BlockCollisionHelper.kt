package cn.solarmoon.spark_core.physics.collision

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel

import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState

import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object BlockCollisionHelper {
    private val SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = WeakHashMap()
    private val DEFAULT_SHAPE = BoxCollisionShape(0.5f)
    
    // 合并判断的配置参数
    private const val MERGE_DISTANCE_THRESHOLD = 1.0 // 合并距离阈值
    private const val BODY_EXPIRATION_TIME = 400 // 碰撞体销毁倒计时

    private fun generateShapeCache(blockState: BlockState): CollisionShape {
        val voxel: VoxelShape =
            blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
        val shape = convertVoxelToCollisionShape(voxel)
        return shape
    }

    // 直接通过BlockState获取缓存的CollisionShape
    fun getCollisionShape(state: BlockState): CollisionShape {
        return SHAPE_CACHE.computeIfAbsent(state) {
            generateShapeCache(it)
        }
    }

    private fun convertVoxelToCollisionShape(voxel: VoxelShape): CollisionShape {
        try {
            val aabb = voxel.bounds()
            val halfExtents = Vector3f(
                (aabb.xsize / 2).toFloat(),
                (aabb.ysize / 2).toFloat(),
                (aabb.zsize / 2).toFloat()
            )
            val box = BoxCollisionShape(halfExtents)
            if (aabb.center.x == 0.5 && aabb.center.y == 0.5 && aabb.center.z == 0.5) return box
            else {
                val compound = CompoundCollisionShape()
                val transform = Transform(
                    Vector3f(
                        aabb.center.x.toFloat() - 0.5f,
                        aabb.center.y.toFloat() - 0.5f,
                        aabb.center.z.toFloat() - 0.5f
                    ),
                    Quaternion.IDENTITY
                )
                compound.addChildShape(box, transform)
                return compound
            }
        } catch (e: Exception) {
            return DEFAULT_SHAPE
        }
    }

    fun addNearbyTerrainBlocksToWorld(pco: PhysicsCollisionObject, physicsLevel: PhysicsLevel) {
        val boundingBox = pco.boundingBox(null)
        val min = boundingBox.getMin(null)
        val max = boundingBox.getMax(null)
        if (pco is PhysicsRigidBody) {
            val v = pco.getLinearVelocity(null)//对于移动物体，额外向速度方向延伸判定区 Extend the detection area in the direction of the velocity for the moving object
            if (v.lengthSquared() < 1600) {//TODO:速度过大(40m/s+)的物体采用其他方法扩展选区 For objects with high speeds (40m/s+), use other methods to extend the selection area
                if (v.x < 0) min.x += v.x * 0.05f else max.x += v.x * 0.05f
                if (v.y < 0) min.y += v.y * 0.05f else max.y += v.y * 0.05f
                if (v.z < 0) min.z += v.z * 0.05f else max.z += v.z * 0.05f
            }
        }
        addTerrainBlocksToWorld(min, max, physicsLevel)
    }

    private fun addTerrainBlocksToWorld(min: Vector3f, max: Vector3f, physicsLevel: PhysicsLevel) {
        // 计算原始的边界值
        val rawMinX = floor(min.x).toInt()
        val rawMaxX = ceil(max.x).toInt()
        val minY = floor(min.y).toInt()
        val maxY = ceil(max.y).toInt()
        val rawMinZ = floor(min.z).toInt()
        val rawMaxZ = ceil(max.z).toInt()
        
        // 计算X和Z轴的宽度
        val xWidth = rawMaxX - rawMinX
        val zWidth = rawMaxZ - rawMinZ
        
        // 取两者中的较大值，确保区域是方形的
        val maxWidth = max(xWidth, zWidth)
        
        // 确保边界是方形的，并添加1方块的缓冲区以获得更好的合并效果
        val halfMaxWidth = maxWidth / 2 + 1
        val centerX = (rawMinX + rawMaxX) / 2
        val centerZ = (rawMinZ + rawMaxZ) / 2
        
        val minX = centerX - halfMaxWidth
        val maxX = centerX + halfMaxWidth
        val minZ = centerZ - halfMaxWidth
        val maxZ = centerZ + halfMaxWidth
        
        // 新增: 用于收集需要合并的方块
        val blocksToBeMerged = mutableListOf<BlockPos>()
        val updatedBlocks = mutableMapOf<BlockPos, BlockState>()
        
        // 第一阶段：收集所有可用方块，记录状态，处理已存在的方块倒计时
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val blockPos = BlockPos(x, y, z)
                    val chunkPos = ChunkPos(blockPos)
                    if (physicsLevel.terrainChunks[chunkPos] != null) {
                        val blockState = physicsLevel.terrainChunks[chunkPos]!!.getBlockState(blockPos)
                        if (physicsLevel.terrainBlockBodies.containsKey(blockPos)) {//如果该位置的方块已经记录过，则检查方块类型后重置销毁倒计时 Reset the destruction count if the block has been recorded

                        } else { //如果该位置的方块没有记录过，检查是否可以用于合并
                            if (!blockState.isAir && !blockState.getCollisionShape(physicsLevel.mcLevel, blockPos).isEmpty) {
                                // 记录方块的状态和坐标用于后续处理
                                updatedBlocks[blockPos] = blockState
                                blocksToBeMerged.add(blockPos)
                            }
                        }
                    }
                }
            }
        }
        
        // 如果没有新方块需要处理，直接返回
        if (blocksToBeMerged.isEmpty()) return
        
        // 第二阶段：合并方块并创建物理刚体
        physicsLevel.submitDeduplicatedTask("merge_blocks", PPhase.PRE) {
            // 将收集到的方块状态记录到terrainBlocks
            updatedBlocks.forEach { (pos, state) ->
                physicsLevel.terrainBlocks[pos] = state
            }
            
            // 创建合并的VoxelShape
            val mergedShape = createVoxelShapeFromBlocks(blocksToBeMerged, physicsLevel)
            
            if (!mergedShape.isEmpty) {
                // 转换为碰撞形状
                val collisionShape = VoxelShapeConverter.toCollisionShape(mergedShape)
                
                // 计算合并后的边界框和中心点
                val bounds = calculateMergedBounds(blocksToBeMerged)
                val centerPos = Vector3f(
                    (bounds.min.x + bounds.max.x) / 2,
                    (bounds.min.y + bounds.max.y) / 2,
                    (bounds.min.z + bounds.max.z) / 2
                )
                
                // 创建一个表示这组合并方块的物理刚体
                val mergedBody = PhysicsRigidBody(
                    "terrain",
                    physicsLevel.mcLevel,
                    collisionShape,
                    0f
                )
                
                // 设置碰撞体属性
                mergedBody.setUserIndex(BODY_EXPIRATION_TIME) // 设定销毁倒计时
                mergedBody.setCollisionGroup(1)
                mergedBody.setCollideWithGroups(1)
                mergedBody.setPhysicsLocation(centerPos)
                
                // 记录每个方块与合并后的刚体的映射关系
                blocksToBeMerged.forEach { pos ->
                    physicsLevel.terrainBlockBodies[pos] = mergedBody
                }
                
                // 将合并后的刚体添加到物理世界
                physicsLevel.world.add(mergedBody)
                
                SparkCore.LOGGER.debug("创建合并碰撞体完成，包含 ${blocksToBeMerged.size} 个方块")
            } else {
                // 如果合并形状为空，则逐个处理方块
                SparkCore.LOGGER.debug("合并形状为空，逐个处理方块")
                blocksToBeMerged.forEach { blockPos ->
                    val blockState = updatedBlocks[blockPos] ?: return@forEach
                    
                    val blockBody = PhysicsRigidBody(
                        "terrain",
                        physicsLevel.mcLevel, 
                        blockState.getBulletCollisionShape(),
                        0f
                    )
                    blockBody.setUserIndex(BODY_EXPIRATION_TIME)
                    blockBody.setPhysicsLocation(
                        Vector3f(
                            blockPos.x.toFloat() + 0.5f,
                            blockPos.y.toFloat() + 0.5f,
                            blockPos.z.toFloat() + 0.5f
                        )
                    )
                    blockBody.tickTransform = blockBody.getTransform(null)
                    blockBody.lastTickTransform = blockBody.tickTransform
                    physicsLevel.terrainBlockBodies[blockPos] = blockBody
                    physicsLevel.world.add(blockBody)
                }
            }
        }
    }

    /**
     * 从方块组创建VoxelShape
     * 将一组连通的方块合并为单个VoxelShape
     */
    private fun createVoxelShapeFromBlocks(blocks: List<BlockPos>, physicsLevel: PhysicsLevel): VoxelShape {
        // 初始化一个空的VoxelShape
        var resultShape = Shapes.empty()

        // 遍历所有方块，获取它们的碰撞形状并合并
        for (pos in blocks) {
            try {
                val blockState = physicsLevel.mcLevel.getBlockState(pos)
                if (!blockState.isAir) {
                    // 获取方块的碰撞形状
                    val blockShape = blockState.getCollisionShape(physicsLevel.mcLevel, pos)
                    if (!blockShape.isEmpty) {
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
}
fun BlockState.getBulletCollisionShape(): CollisionShape {
    return BlockCollisionHelper.getCollisionShape(this)
}
/**
 * 边界框数据类
 */
data class BoundingBox(
    val min: Vector3f,
    val max: Vector3f
)