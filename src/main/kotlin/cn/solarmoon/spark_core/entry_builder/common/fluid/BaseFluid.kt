package cn.solarmoon.spark_core.fluid

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.PushReaction
import net.neoforged.neoforge.fluids.BaseFlowingFluid


/**
 * 由于大多数液体和水基本一致，所以搞个和水差不多的基本类
 *
 * 增加了默认的桶和液体本体方块
 */
abstract class BaseFluid(properties: Properties) : BaseFlowingFluid(properties) {//

    val underFluidParticle: ParticleOptions
        get() = ParticleTypes.UNDERWATER

    /**
     * 与水一致
     *
     * 特指音效、粒子动画等
     */
    override fun animateTick(level: Level, pos: BlockPos, state: FluidState, randomSource: RandomSource) {
        if (!state.isSource && !state.getValue(FALLING)) {
            if (randomSource.nextInt(64) == 0) {
                level.playLocalSound(pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5,
                    SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS,
                    randomSource.nextFloat() * 0.25f + 0.75f,
                    randomSource.nextFloat() + 0.5f,
                    false
                )
            }
        } else if (randomSource.nextInt(10) == 0) {
            level.addParticle(underFluidParticle,
                pos.x.toDouble() + randomSource.nextDouble(), pos.y.toDouble() + randomSource.nextDouble(), pos.z.toDouble() + randomSource.nextDouble(),
                0.0, 0.0, 0.0
            )
        }
    }

    /**
     * 液体方块
     */
    open class FluidBlock(flowingFluid: FlowingFluid): LiquidBlock(//
        flowingFluid, Properties.of()
            .replaceable() // 可替换液体为方块
            .noCollission()
            .strength(100.0f)
            .pushReaction(PushReaction.DESTROY) // 活塞推动摧毁
            .noLootTable()
            .liquid()
    )

    /**
     * 对应桶
     */
    open class Bucket(content: Fluid) : BucketItem(content, Properties().craftRemainder(Items.BUCKET).stacksTo(1))

    // 下面两个和BaseFlowingFluid方法里的一致，为了统一搬过来

    open class Flowing(properties: Properties) : BaseFlowingFluid.Flowing(properties)

    open class Source(properties: Properties) : BaseFlowingFluid.Source(properties)

}