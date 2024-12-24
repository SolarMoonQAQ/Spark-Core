package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class BlockBuilder<B : Block>(private val blockDeferredRegister: DeferredRegister<Block>) {

    private var id: String = ""
    private var block: Supplier<B>? = null

    fun id(id: String) = apply { this.id = id }
    fun bound(block: Supplier<B>) = apply { this.block = block }

    fun build(): DeferredHolder<Block, B> = blockDeferredRegister.register(id, block!!)

}