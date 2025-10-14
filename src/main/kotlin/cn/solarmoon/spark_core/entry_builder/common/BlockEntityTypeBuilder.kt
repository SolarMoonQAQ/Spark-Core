package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.BlockCapability
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class BlockEntityTypeBuilder<B : BlockEntity>(
    deferredRegister: DeferredRegister<BlockEntityType<*>>,
    private val bus: IEventBus
) : RegisterBuilder<BlockEntityType<*>, BlockEntityType<B>>(deferredRegister) {

    private val caps = mutableListOf<Pair<BlockCapability<*, *>, ICapabilityProvider<B, *, *>>>()

    lateinit var factory: (BlockPos, BlockState) -> B

    // 用 DSL 容器来收集 block
    private val validBlocksList = mutableListOf<Block>()

    fun validBlocks(block: ValidBlocksDsl.() -> Unit) = apply {
        validBlocksList.clear()
        ValidBlocksDsl().apply(block)
    }

    inner class ValidBlocksDsl {
        operator fun Block.unaryPlus() {
            validBlocksList += this
        }
    }

    fun capabilities(block: CapabilityDsl.() -> Unit) = apply {
        CapabilityDsl().apply(block)
    }

    inner class CapabilityDsl {
        infix fun BlockCapability<*, *>.with(provider: ICapabilityProvider<B, *, *>) {
            caps += this to provider
        }
    }

    override fun validate() {
        super.validate()
        if (!this::factory.isInitialized) {
            throw IllegalStateException("未给 ${javaClass.simpleName} 指定构造函数!")
        }
    }

    override fun build(): DeferredHolder<BlockEntityType<*>, BlockEntityType<B>> {
        val reg = deferredRegister.register(id, Supplier {
            BlockEntityType.Builder.of(factory, *validBlocksList.toTypedArray()).build(null)
        })
        if (caps.isNotEmpty()) {
            bus.addListener { e: RegisterCapabilitiesEvent ->
                for ((cap, provider) in caps) {
                    e.registerBlockEntity(
                        cap as BlockCapability<Any, Any?>,
                        reg.get(),
                        provider as ICapabilityProvider<B, Any?, Any>
                    )
                }
            }
        }
        return reg
    }
}



