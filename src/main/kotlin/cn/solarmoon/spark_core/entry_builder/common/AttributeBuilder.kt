package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.core.Holder
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.RangedAttribute
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier


class AttributeBuilder(private val modId: String, private val attributeRegister: DeferredRegister<Attribute>, private val bus: IEventBus) {//

    private var id = ""
    private var attributeSupplier: Supplier<Attribute>? = null
    private var applyToLiving = false

    fun id(id: String) = apply { this.id = id }

    fun bound(attributeSupplier: Supplier<Attribute>) = apply { this.attributeSupplier = attributeSupplier }

    fun boundRanged(defaultValue: Double, min: Double, max: Double) = apply {
        this.attributeSupplier = Supplier { RangedAttribute("attribute.$modId.$id", defaultValue, min, max) }
    }

    fun applyToLivingEntity() = apply { this.applyToLiving = true }

    fun build(): DeferredHolder<Attribute, Attribute> {
        val entry = attributeRegister.register(id, attributeSupplier!!)
        bus.addListener{ e: EntityAttributeModificationEvent -> applyAttribute(entry, e) }
        return entry
    }

    private fun applyAttribute(attribute: Holder<Attribute>, event: EntityAttributeModificationEvent) {
        if (applyToLiving) {
            for (entity in event.types) {
                event.add(entity, attribute)
            }
        }
    }

}