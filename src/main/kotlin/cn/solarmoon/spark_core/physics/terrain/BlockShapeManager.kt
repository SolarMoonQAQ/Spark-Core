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

    /**
     * 方块包围盒缓存（BlockState → AABB列表），供投射物精确命中判定使用。
     * 在 [getCollisionShape] 首次构造 CollisionShape 时同步填充。
     */
    val AABB_CACHE: MutableMap<BlockState, List<net.minecraft.world.phys.AABB>> = ConcurrentHashMap()

    private fun generateShapeCache(blockState: BlockState): CollisionShape {
        val shape = convertVoxelToCollisionShape(blockState)
        // 预缓存 AABB 列表供投射物命中判定使用（提前计算，避免物理线程热路径首次访问开销）
        AABB_CACHE.computeIfAbsent(blockState) {
            blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                .toAabbs()
        }
        return shape
    }

    // 直接通过BlockState获取缓存的CollisionShape
    fun getCollisionShape(state: BlockState): CollisionShape {
        return SHAPE_CACHE.computeIfAbsent(state) {
            generateShapeCache(it)
        }
    }

    /**
     * 获取方块的实际碰撞包围盒列表，与 [getCollisionShape] 共享同一 VoxelShape 数据源。
     * 对于大多数方块返回单个 AABB；对于楼梯、栅栏等复杂形状返回多个子包围盒。
     */
    fun getBlockAabbs(state: BlockState): List<net.minecraft.world.phys.AABB> {
        return AABB_CACHE.computeIfAbsent(state) {
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                .toAabbs()
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