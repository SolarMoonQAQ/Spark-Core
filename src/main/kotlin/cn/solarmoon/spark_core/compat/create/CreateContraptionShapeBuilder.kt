package cn.solarmoon.spark_core.compat.create

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.simibubi.create.content.contraptions.Contraption
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext

/**
 * Create 装置碰撞形状构建器（体素形状优先）。
 *
 * 构建流程：
 * 1) 遍历 contraption 的本地方块；
 * 2) 读取每个方块在 ContraptionWorld 中的 collision shape；
 * 3) 将每个体素 box 转为 Bullet 的 BoxCollisionShape 子形状；
 * 4) 同步建立“子形状索引 -> 本地方块坐标”映射。
 */
object CreateContraptionShapeBuilder {

    /**
     * 构建结果。
     *
     * @property shape 最终复合碰撞形状
     * @property childShapeToLocalBlock 子形状索引到本地方块坐标映射
     * @property localBlockStates 本地方块坐标到 BlockState 映射
     */
    data class BuildResult(
        val shape: CompoundCollisionShape,
        val childShapeToLocalBlock: Map<Int, BlockPos>,
        val localBlockStates: Map<BlockPos, BlockState>
    )

    /**
     * 根据 Contraption 当前方块结构构建碰撞体。
     *
     * 线程语义：
     * - 建议在物理线程调用；
     * - 该方法为纯计算逻辑，不向世界提交任何副作用操作。
     */
    fun build(contraption: Contraption): BuildResult {
        val shape = CompoundCollisionShape()
        val childShapeToLocalBlock = LinkedHashMap<Int, BlockPos>()
        val localBlockStates = LinkedHashMap<BlockPos, BlockState>()
        val collisionContext = CollisionContext.empty()
        var childShapeIndex = 0

        // 通过 contraption world 获取碰撞体积，与 Create 原生碰撞判定保持一致。
        val contraptionWorld = contraption.contraptionWorld
        val blockMap = contraption.blocks

        for ((localPos, structureInfo) in blockMap) {
            val blockState = structureInfo.state()
            localBlockStates[localPos] = blockState

            val voxelShape = blockState.getCollisionShape(contraptionWorld, localPos, collisionContext)
            if (voxelShape.isEmpty) continue

            // 逐个体素盒写入复合形状，并记录子形状索引到方块坐标映射。
            voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                val extentX = ((maxX - minX) * 0.5).toFloat()
                val extentY = ((maxY - minY) * 0.5).toFloat()
                val extentZ = ((maxZ - minZ) * 0.5).toFloat()
                if (extentX <= 0f || extentY <= 0f || extentZ <= 0f) return@forAllBoxes

                // 与 Create 变换公式保持一致：
                // world = rotate(local - 0.5) + 0.5 + anchor
                // 因此子形状局部中心需要先减去 0.5，刚体原点再放到 anchor + 0.5。
                val centerX = (localPos.x + (minX + maxX) * 0.5 - 0.5).toFloat()
                val centerY = (localPos.y + (minY + maxY) * 0.5 - 0.5).toFloat()
                val centerZ = (localPos.z + (minZ + maxZ) * 0.5 - 0.5).toFloat()

                val childShape = BoxCollisionShape(Vector3f(extentX, extentY, extentZ))
                shape.addChildShape(childShape, Transform(Vector3f(centerX, centerY, centerZ), Quaternion.IDENTITY))
                childShapeToLocalBlock[childShapeIndex] = localPos
                childShapeIndex++
            }
        }

        return BuildResult(shape, childShapeToLocalBlock, localBlockStates)
    }
}
