package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class SoundBuilder(private val modId: String, private val soundDeferredRegister: DeferredRegister<SoundEvent>) {//

    private var id = ""

    fun id(id: String) = apply { this.id = id }

    fun build(): DeferredHolder<SoundEvent, SoundEvent> {
        return soundDeferredRegister.register(id, Supplier { SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(modId, id)) })
    }

}