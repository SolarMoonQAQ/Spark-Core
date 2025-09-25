package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.component.Authority
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.level.ServerPhysicsLevel
import cn.solarmoon.spark_core.registry.common.SparkCollisionObjectTypes
import cn.solarmoon.spark_core.util.BlockCollisionUtil
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
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

object BlockCollisionHelper {
    private val SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = WeakHashMap()

    //TODO:服务端不同维度仍需作出区分
    private val SERVER_SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = WeakHashMap()
    private val DEFAULT_SHAPE = BoxCollisionShape(0.5f)

    private fun generateShapeCache(blockState: BlockState): CollisionShape {
        val voxel: VoxelShape =
            blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
        val shape = convertVoxelToCollisionShape(voxel)
        shape.margin = 0.01f
        return shape
    }

    // 直接通过BlockState获取缓存的CollisionShape
    fun getCollisionShape(state: BlockState, physicsLevel: PhysicsLevel): CollisionShape {
        if (physicsLevel is ServerPhysicsLevel)
            return SERVER_SHAPE_CACHE.computeIfAbsent(state) {
                generateShapeCache(it)
            }
        else
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

    fun gatherNearbyTerrainBlocksForWorld(pco: PhysicsCollisionObject, physicsLevel: PhysicsLevel) {
        val boundingBox = pco.boundingBox(null)
        val min = boundingBox.getMin(null)
        val max = boundingBox.getMax(null)
        val blocks = physicsLevel.terrainBlocks
        if (pco is PhysicsRigidBody) {
            val v =
                pco.getLinearVelocity(null)//对于移动物体，额外向速度方向延伸判定区 Extend the detection area in the direction of the velocity for the moving object
            if (v.lengthSquared() < 1600) {//TODO:速度过大(40m/s+)的物体采用其他方法扩展选区 For objects with high speeds (40m/s+), use other methods to extend the selection area
                if (v.x < 0) min.x += v.x * 0.05f else max.x += v.x * 0.05f
                if (v.y < 0) min.y += v.y * 0.05f else max.y += v.y * 0.05f
                if (v.z < 0) min.z += v.z * 0.05f else max.z += v.z * 0.05f
                val minX = floor(min.x).toInt()
                val maxX = ceil(max.x).toInt()
                val minY = floor(min.y).toInt()
                val maxY = ceil(max.y).toInt()
                val minZ = floor(min.z).toInt()
                val maxZ = ceil(max.z).toInt()
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            blocks.add(BlockPos(x, y, z))
                        }
                    }
                }
            }
        }
    }

    fun addOrUpdateTerrainBlocksToWorld(blockPoses: Set<BlockPos>, physicsLevel: PhysicsLevel) {
        for (blockPos in blockPoses) {
            val chunkPos = ChunkPos(blockPos)
            if (physicsLevel.terrainChunks[chunkPos] != null) {
                val blockState = physicsLevel.terrainChunks[chunkPos]!!.getBlockState(blockPos)
                if (physicsLevel.terrainBlockBodies.containsKey(blockPos)) {//如果该位置的方块已经记录过，则检查方块类型后重置销毁倒计时 Reset the destruction count if the block has been recorded
                    val block = physicsLevel.terrainBlockBodies[blockPos]
                    if (blockState.isAir || blockState.getCollisionShape(physicsLevel.mcLevel, blockPos).isEmpty
                    ) {//如果该位置的方块已经是空气或可替换方块，则销毁倒计时立刻归零
                        block?.userIndex = 0
                    } else if (block?.userIndex!! > 0) {//重置销毁倒计时 Reset the destruction count
                        //更新方块打滑属性(默认取决于方块类型，上方方块，和天气)
                        if (Math.random() > 0.95)
                            block.userIndex2 = BlockCollisionUtil.getSlip(
                                physicsLevel.terrainChunks[chunkPos],
                                blockState,
                                blockPos
                            )
                        block.userIndex = 10
                    }
                } else {//如果该位置的方块没有记录过，则获取块状态并创建刚体对象 Create a physics body for the block if it has not been recorded
                    if (!blockState.isAir && !blockState.getCollisionShape(
                            physicsLevel.mcLevel,
                            blockPos
                        ).isEmpty
                    ) {
                        val slip =
                            BlockCollisionUtil.getSlip(physicsLevel.terrainChunks[chunkPos], blockState, blockPos)
                        // 如果块不是空气或可替换方块，记录方块的状态和坐标 Record the block state and coordinates
                        SparkCollisionObjectTypes.RIGID_BODY.get()
                            .create(blockPos.toString(), Authority.BOTH, physicsLevel.mcLevel).apply {
                                body.collisionShape = blockState.getBulletCollisionShape(physicsLevel)
                                mass = 0f
                                userIndex = 10
                                position = org.joml.Vector3f(
                                    blockPos.x.toFloat() + 0.5f,
                                    blockPos.y.toFloat() + 0.5f,
                                    blockPos.z.toFloat() + 0.5f
                                )
                                friction =
                                    BlockCollisionUtil.getBlockFriction(physicsLevel.mcLevel, blockState, blockPos)
                                rollingFriction = BlockCollisionUtil.getBlockRollingFriction(
                                    physicsLevel.mcLevel,
                                    blockState,
                                    blockPos
                                )
                                restitution = BlockCollisionUtil.getRestitution(
                                    physicsLevel.terrainChunks[chunkPos],
                                    blockState,
                                    blockPos
                                )
                                userIndex2 = (slip)
                                collisionGroup = CollisionGroups.TERRAIN
                                collideWithGroups = CollisionGroups.NONE
                                contactStiffness = 1e20f
                                physicsLevel.terrainBlockBodies[blockPos] = this
                                bindHost(level)
                                addToLevel()
                            }
                    }
                }
            }
        }
    }
}

fun BlockState.getBulletCollisionShape(level: PhysicsLevel): CollisionShape {
    return BlockCollisionHelper.getCollisionShape(this, level)
}