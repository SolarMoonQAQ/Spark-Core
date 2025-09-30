package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.util.toRadians
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.joml.Matrix4f
import org.joml.Quaternionf
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f
import kotlin.math.PI

interface IBlockEntityAnimatable<B: BlockEntity>: IAnimatable<B> {

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f {
        return Matrix4f()
            .translate(animatable.blockPos.toVector3f().add(0.5f, 0.0f, 0.5f))
            .rotate(animatable.blockState.getOptionalValue(BlockStateProperties.FACING).takeIf { it.isPresent }?.get()?.rotation ?: Quaternionf())
    }

    override val animLevel: Level?
        get() = animatable.level

}