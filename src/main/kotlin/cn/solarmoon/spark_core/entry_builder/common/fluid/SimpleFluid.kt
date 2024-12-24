package cn.solarmoon.spark_core.fluid

import cn.solarmoon.spark_core.entry_builder.common.fluid.FluidBuilder
import net.neoforged.neoforge.fluids.BaseFlowingFluid

open class SimpleFluid(private val entry: FluidBuilder.FluidEntry) {//

    val flowing
        get() = BaseFluid.Flowing(makeProperties(entry))
    val source
        get() = BaseFluid.Source(makeProperties(entry))
    val block
        get() = BaseFluid.FluidBlock(entry.source.get())
    val bucket
        get() = BaseFluid.Bucket(entry.source.get())

    companion object {
        @JvmStatic
        private fun makeProperties(entry: FluidBuilder.FluidEntry):  BaseFlowingFluid.Properties {
            return BaseFlowingFluid.Properties(
                entry.type,
                entry.source,
                entry.flowing
            )
                .block(entry.block)
                .bucket(entry.bucket)
        }
    }

}