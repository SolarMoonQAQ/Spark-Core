package cn.solarmoon.spark_core.physics.collision

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

object BlockShapeHelper {
    private val SHAPE_CACHE: MutableMap<BlockState, CollisionShape> = WeakHashMap()
    private val DEFAULT_SHAPE = BoxCollisionShape(0.5f)

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
}

fun BlockState.getBulletCollisionShape(): CollisionShape {
    return BlockShapeHelper.getCollisionShape(this)
}