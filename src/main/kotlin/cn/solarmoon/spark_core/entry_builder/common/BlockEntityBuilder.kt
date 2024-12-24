package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.BlockCapability
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class BlockEntityBuilder<B : BlockEntity>(//
    private val blockEntityDeferredRegister: DeferredRegister<BlockEntityType<*>>,
    private val modBus: IEventBus
) {

    private var id: String = ""
    private var be: BlockEntitySupplier<B>? = null
    private var validBlocks: Supplier<Array<Block>>? = null
    private var capP: MutableList<Pair<BlockCapability<*, *>, ICapabilityProvider<B, *, *>>> = mutableListOf()

    fun id(id: String) = apply { this.id = id }
    fun bound(blockentity: BlockEntitySupplier<B>) = apply { this.be = blockentity }
    fun validBlocks(blocks: Supplier<Array<Block>>) = apply { this.validBlocks = blocks }

    fun <T, C: Any?> capability(
        cap: BlockCapability<T, C>,
        provider: ICapabilityProvider<B, C, T>
    ) = apply {
        capP.add(Pair(cap, provider))
    }

    fun build(): DeferredHolder<BlockEntityType<*>, BlockEntityType<B>> {
        val reg = blockEntityDeferredRegister.register(id, Supplier {
            BlockEntityType.Builder.of(be!!, *validBlocks!!.get()).build(null)
        })
        if (!capP.isEmpty()) modBus.addListener { e: RegisterCapabilitiesEvent -> registerCap(reg, e) }
        return reg
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerCap(reg: DeferredHolder<BlockEntityType<*>, BlockEntityType<B>>, event: RegisterCapabilitiesEvent) {
        for (cap in capP) {
            event.registerBlockEntity(cap.first as BlockCapability<Any, Any?>, reg.get(), cap.second as ICapabilityProvider<B, Any?, Any>)
        }
    }

}


