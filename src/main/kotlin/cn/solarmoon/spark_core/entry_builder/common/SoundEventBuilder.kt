package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class SoundEventBuilder(
    dfr: DeferredRegister<SoundEvent>
): RegisterBuilder<SoundEvent, SoundEvent>(dfr) {

    override fun build(): DeferredHolder<SoundEvent, SoundEvent> {
        return deferredRegister.register(id, Supplier { SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(modId, id)) })
    }

}