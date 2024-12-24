package cn.solarmoon.spark_core.entry_builder.common.fluid

import cn.solarmoon.spark_core.fluid.BaseFluidType
import cn.solarmoon.spark_core.fluid.SimpleFluid
import cn.solarmoon.spark_core.fluid.WaterLikeFluidType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.FluidType.Properties
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier


class FluidBuilder(
    private val modId: String,
    private val fluidDeferredRegister: DeferredRegister<Fluid>,
    private val fluidTypeDeferredRegister: DeferredRegister<FluidType>,
    private val blockDeferredRegister: DeferredRegister<Block>,
    private val itemDeferredRegister: DeferredRegister<Item>,
    private val modBus: IEventBus
) {//

    private var id: String = ""
    private var source: Supplier<FlowingFluid>? = null
    private var flowing: Supplier<FlowingFluid>? = null
    private var type: Supplier<FluidType>? = null
    private var block: Supplier<LiquidBlock>? = null
    private var bucket: Supplier<Item>? = null
    private val defaultColor = (0xFFFFFFFF).toInt()

    fun id(id: String) = apply { this.id = id }

    fun source(source: Supplier<FlowingFluid>) = apply { this.source = source }

    fun flowing(flowing: Supplier<FlowingFluid>) = apply { this.flowing = flowing }

    fun type(type: Supplier<FluidType>) = apply { this.type = type }

    fun block(block: Supplier<LiquidBlock>) = apply { this.block = block }

    fun bucket(bucket: Supplier<Item>) = apply { this.bucket = bucket }

    fun waterLike(defaultUnderOverlay: Boolean, properties: Properties) = apply {
        type = Supplier { WaterLikeFluidType(defaultUnderOverlay, modId, id, defaultColor, properties) }
    }

    fun waterLike(defaultUnderOverlay: Boolean, color: Int, properties: Properties) = apply {
        type = Supplier { WaterLikeFluidType(defaultUnderOverlay, modId, id, color, properties) }
    }

    fun base(properties: Properties) = apply {
        type = Supplier { BaseFluidType(modId, id, defaultColor, properties) }
    }

    fun base(color: Int, properties: Properties) = apply {
        type = Supplier { BaseFluidType(modId, id, color, properties) }
    }

    /**
     * 使用[SimpleFluid]类快速创建液体所需内容
     */
    fun simple(simpleFluidSupplier: Supplier<SimpleFluid>, hasBucket: Boolean) = apply {
        block { simpleFluidSupplier.get().block }
        source { simpleFluidSupplier.get().source }
        flowing { simpleFluidSupplier.get().flowing }
        if (hasBucket) bucket { simpleFluidSupplier.get().bucket }
    }

    fun build(): FluidEntry {
        if (FMLEnvironment.dist.isClient) modBus.addListener(this::registerClientExtension)
        val bucket = bucket?.let { itemDeferredRegister.register(id + "_bucket", it) }
        modBus.addListener{ e: RegisterCapabilitiesEvent -> registerBucketCap(bucket, e) }
        return FluidEntry(
            fluidDeferredRegister.register(id, source!!),
            fluidDeferredRegister.register(id + "_flowing", flowing!!),
            fluidTypeDeferredRegister.register(id, type!!),
            blockDeferredRegister.register(id, block!!),
            bucket
        )
    }

    class FluidEntry(
        val source: DeferredHolder<Fluid, FlowingFluid>,
        val flowing: DeferredHolder<Fluid, FlowingFluid>,
        val type: DeferredHolder<FluidType, FluidType>,
        val block: DeferredHolder<Block, LiquidBlock>,
        val bucket: DeferredHolder<Item, Item>?
    )

    private fun registerClientExtension(event: RegisterClientExtensionsEvent) {
        val t = type!!.get()
        if (t is BaseFluidType) {
            event.registerFluidType(t.getClientExtension(), t)
        }
    }

    private fun registerBucketCap(bucket: DeferredHolder<Item, Item>?, event: RegisterCapabilitiesEvent) {
        bucket?.let { event.registerItem(Capabilities.FluidHandler.ITEM, { stack, _ -> FluidBucketWrapper(stack) }, it.get()) }
    }

}