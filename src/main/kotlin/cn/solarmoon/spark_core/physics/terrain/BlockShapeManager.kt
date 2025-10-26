package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 生成或获取某一blockState对应的方块碰撞体积
 * 各物理世界独立持有，避免竞态问题
 */
class BlockShapeManager(val physicsLevel: PhysicsLevel) {
    val SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = ConcurrentHashMap()
    val FULL_BLOCK = BoxCollisionShape(0.5f)
    val UP_HALF_BLOCK = BoxCollisionShape(0.5f, 0.25f, 0.5f)
    val DOWN_HALF_BLOCK = BoxCollisionShape(0.5f, 0.25f, 0.5f)

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

    private fun convertVoxelToCollisionShape(blockState: BlockState): CollisionShape {
        if (blockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return FULL_BLOCK
        val voxel = blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
        if (isXZFullShape(blockState)) {
            if (voxel.min(Direction.Axis.Y) == 0.0 && voxel.max(Direction.Axis.Y) == 0.5) return UP_HALF_BLOCK
            else if (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1.0) return DOWN_HALF_BLOCK
        }
        try {
            val aabb = voxel.bounds()
            val halfExtents = Vector3f(
                (aabb.xsize / 2).toFloat(),
                (aabb.ysize / 2).toFloat(),
                (aabb.zsize / 2).toFloat()
            )
            val box = BoxCollisionShape(halfExtents)
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
            return FULL_BLOCK
        }
    }

    fun isXZFullShape(state: BlockState): Boolean {
        val voxel = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
        return (voxel.min(Direction.Axis.X) == 0.0 && voxel.min(Direction.Axis.Z) == 0.0
                && voxel.max(Direction.Axis.X) == 1.0 && voxel.max(Direction.Axis.Z) == 1.0)
    }

    /**
     * 判断形状是否是可合并的简单形状
     */
    fun isMergeableShape(state: BlockState): Boolean {
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return true
        else if (isXZFullShape(state)){
            val voxel = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
            return if (voxel.min(Direction.Axis.Y) == 0.0 && voxel.max(Direction.Axis.Y) == 0.5) true
            else if (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1.0) true
            else false
        }
        return false
    }

    /**
     * 获取形状的类型标识，用于合并时的形状匹配
     */
    private fun getShapeType(shape: CollisionShape): Int {
        return when (shape) {
            FULL_BLOCK -> 1
            UP_HALF_BLOCK -> 2
            DOWN_HALF_BLOCK -> 3
            else -> 0 // 0表示不可合并的复杂形状
        }
    }

    /**
     * 获取形状的类型标识，用于合并时的形状匹配
     */
    fun getShapeType(state: BlockState): Int {
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return 1
        else if (isXZFullShape(state)){
            val voxel = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
            return if (voxel.min(Direction.Axis.Y) == 0.0 && voxel.max(Direction.Axis.Y) == 0.5) 3
            else if (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1.0) 2
            else 0
        } else {
            val shape = state.getBulletCollisionShape(physicsLevel)
            return getShapeType(shape)
        }
    }
}

fun BlockState.getBulletCollisionShape(level: PhysicsLevel): CollisionShape {
    return level.blockShapeManager.getCollisionShape(this)
}