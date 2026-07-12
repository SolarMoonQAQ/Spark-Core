package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.util.toVector3f
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.joml.Matrix4f
import org.joml.Quaternionf

interface IBlockEntityAnimatable<B: BlockEntity>: IAnimatable<B> {

    override val defaultModelIndex: ModelIndex get() = ModelIndex.of(animatable.type)

    /**
     * 轻量世界坐标查询——直接取方块实体中心坐标，零矩阵分配。
     */
    override fun getRenderPosition(partialTicks: Number): Vec3 {
        return Vec3.atBottomCenterOf(animatable.blockPos)
    }

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f {
        return Matrix4f()
            .translate(animatable.blockPos.toVector3f().add(0.5f, 0.0f, 0.5f))
            .rotate(animatable.blockState.getOptionalValue(BlockStateProperties.FACING).takeIf { it.isPresent }?.get()?.rotation ?: Quaternionf())
    }

    override val animLevel: Level?
        get() = animatable.level

}