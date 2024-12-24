package cn.solarmoon.spark_core.entry_builder.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier

class KeyMappingBuilder(private val modId: String, private val bus: IEventBus) {

    private var id = ""
    private var key: Int? = null
    private var conflictContext = KeyConflictContext.UNIVERSAL
    private var modifier = KeyModifier.NONE
    private var inputType = InputConstants.Type.KEYSYM
    private var category: String? = null

    fun id(id: String) = apply { this.id = id }
    fun bound(key: Int) = apply { this.key = key }
    fun conflictContext(context: KeyConflictContext) = apply { this.conflictContext = context }
    fun modifier(km: KeyModifier) = apply { modifier = km }
    fun type(type: InputConstants.Type) = apply { this.inputType = type }
    fun category(name: String) = apply { category = name }

    fun build(): KeyMapping {
        val key = KeyMapping("key.${modId}.${id}", conflictContext, modifier, inputType, key!!, category ?: "key.categories.${modId}")
        bus.addListener { e: RegisterKeyMappingsEvent -> initKey(key, e) }
        return key
    }

    private fun initKey(key: KeyMapping, event: RegisterKeyMappingsEvent) {
        event.register(key)
    }

}