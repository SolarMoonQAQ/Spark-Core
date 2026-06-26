package cn.solarmoon.sparkcore.minecraft.entry_builder.common

import cn.solarmoon.sparkcore.minecraft.entry_builder.CommonRegisterBuilder
import net.minecraft.world.entity.ai.attributes.Attribute
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister


class AttributeBuilder<A: Attribute>(
    attributeRegister: DeferredRegister<Attribute>,
    private val bus: IEventBus
): CommonRegisterBuilder<Attribute, A>(attributeRegister) {

    var applyToLiving = false

    override fun build(): DeferredHolder<Attribute, A> {
        val entry = super.build()
        if (applyToLiving) {
            bus.addListener { e: EntityAttributeModificationEvent ->
                for (entity in e.types) {
                    e.add(entity, entry)
                }
            }
        }
        return entry
    }

}
