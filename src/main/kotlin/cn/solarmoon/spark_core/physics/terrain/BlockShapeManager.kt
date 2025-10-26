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
    val FULL_BLOCK = BoxCollisionShape(0.5f)
    val UP_HALF_BLOCK = CompoundCollisionShape(1).addChildShape(BoxCollisionShape(0.5f, 0.25f, 0.5f), Vector3f(0f, 0.375f, 0f))
    val DOWN_HALF_BLOCK = CompoundCollisionShape(1).addChildShape(BoxCollisionShape(0.5f, 0.25f, 0.5f), Vector3f(0f, -0.375f, 0f))

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
        if(blockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return FULL_BLOCK
        val voxel = blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
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

    /**
     * 判断形状是否是可合并的简单形状
     */
    fun isMergeableShape(shape: CollisionShape): Boolean {
        return shape == FULL_BLOCK ||
                shape == UP_HALF_BLOCK ||
                shape == DOWN_HALF_BLOCK
        // 后续可以添加更多可合并的形状，如不同高度的雪层等
    }

    /**
     * 获取形状的类型标识，用于合并时的形状匹配
     */
    fun getShapeType(shape: CollisionShape): Int {
        return when (shape) {
            FULL_BLOCK -> 1
            UP_HALF_BLOCK -> 2
            DOWN_HALF_BLOCK -> 3
            else -> 0 // 0表示不可合并的复杂形状
        }
    }
}

fun BlockState.getBulletCollisionShape(level: PhysicsLevel): CollisionShape {
    return level.blockShapeManager.getCollisionShape(this)
}