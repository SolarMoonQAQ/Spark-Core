package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 生成或获取某一blockState对应的方块碰撞体积
 * 各物理世界独立持有，避免竞态问题
 */
class BlockShapeManager(val physicsLevel: PhysicsLevel) {
    val SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = ConcurrentHashMap()
    val STATE_CACHE: MutableMap<CollisionShape, BlockState> = ConcurrentHashMap()
    private val DEFAULT_SHAPE = BoxCollisionShape(0.5f)

    private fun generateShapeCache(blockState: BlockState): CollisionShape {
        val shape = convertVoxelToCollisionShape(blockState)
        return shape
    }

    // 直接通过BlockState获取缓存的CollisionShape
    fun getCollisionShape(state: BlockState): CollisionShape {
        return SHAPE_CACHE.computeIfAbsent(state) {
            generateShapeCache(it)
        }
    }

    fun getBlockState(shape: CollisionShape): BlockState? {
        return if (shape is CompoundCollisionShape) {
            STATE_CACHE[shape.listChildren()[0].shape]
        } else STATE_CACHE[shape]
    }

    private fun convertVoxelToCollisionShape(blockState: BlockState): CollisionShape {
        val voxel: VoxelShape =
            blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
        try {
            val aabb = voxel.bounds()
            val halfExtents = Vector3f(
                (aabb.xsize / 2).toFloat(),
                (aabb.ysize / 2).toFloat(),
                (aabb.zsize / 2).toFloat()
            )
            val box = BoxCollisionShape(halfExtents)
            STATE_CACHE[box] = blockState
            if (aabb.center.x == 0.5 && aabb.center.y == 0.5 && aabb.center.z == 0.5) {
                return box
            } else {
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
}

fun BlockState.getBulletCollisionShape(level: PhysicsLevel): CollisionShape {
    return level.blockShapeManager.getCollisionShape(this)
}

fun CollisionShape.getBlockState(level: PhysicsLevel): BlockState? {
    return level.blockShapeManager.getBlockState(this)
}