package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.item.CreativeModeTab
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class CreativeTabBuilder(private val creativeTabDeferredRegister: DeferredRegister<CreativeModeTab>) {//

    private var id = ""
    private var builder: CreativeModeTab.Builder? = null

    fun id(id: String) = apply { this.id = id }

    fun bound(builder: CreativeModeTab.Builder) = apply { this.builder = builder }

    fun build(): DeferredHolder<CreativeModeTab, CreativeModeTab> {
        return creativeTabDeferredRegister.register(id, Supplier { builder!!.build() })
    }

}