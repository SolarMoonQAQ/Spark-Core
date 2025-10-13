package cn.solarmoon.spark_core.entry_builder.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.neoforged.bus.api.IEventBus
import net.neoforged.jarjar.nio.util.Lazy
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier

class KeyMappingBuilder(val modId: String, val bus: IEventBus) {

    lateinit var id: String
    var key: Int? = null
    var conflictContext = KeyConflictContext.UNIVERSAL
    var modifier = KeyModifier.NONE
    var inputType = InputConstants.Type.KEYSYM
    var category: String? = null

    fun build(): Lazy<KeyMapping> {
        val key = Lazy.of { KeyMapping("key.${modId}.${id}", conflictContext, modifier, inputType, key!!, category ?: "key.categories.${modId}") }
        bus.addListener { e: RegisterKeyMappingsEvent -> initKey(key.get(), e) }
        return key
    }

    fun initKey(key: KeyMapping, event: RegisterKeyMappingsEvent) {
        event.register(key)
    }

}