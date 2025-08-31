package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.registry.dynamic.DynamicIdManager
import cn.solarmoon.spark_core.registry.virtual.VirtualRegistry
import cn.solarmoon.spark_core.resource.payload.registry.DynamicRegistrySyncS2CPacket
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

object SparkRegistries {

    // Hot-reload registries: Virtual only, not part of vanilla/NeoForge registries
    @JvmStatic
    val TYPED_ANIMATION = VirtualRegistry<TypedAnimation>(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_animation")
    ).apply {
        this.onDynamicRegister = { key, value ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            val registryName = this.key().location().toString()
                            val assignedId = DynamicIdManager.getId(registryName, key.location()) ?: -1
                            val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimationAdd(key.location().namespace, key.location(), value, assignedId)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        this.onDynamicUnregister = { key, _ ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            DynamicRegistrySyncS2CPacket.syncTypedAnimationRemovalToClients(key.location().namespace, key.location())
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    val MODELS = VirtualRegistry<OModel>(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "models"),
    ).apply {
        this.onDynamicRegister = { key, value ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            val registryName = this.key().location().toString()
                            val assignedId = DynamicIdManager.getId(registryName, key.location()) ?: -1
                            val packet = DynamicRegistrySyncS2CPacket.createForModelAdd(key.location().namespace, key.location(), value, assignedId)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        this.onDynamicUnregister = { key, _ ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            DynamicRegistrySyncS2CPacket.syncModelRemovalToClients(key.location().namespace, key.location())
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    val DYNAMIC_TEXTURES = VirtualRegistry<OTexture>(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "dynamic_textures"),
    ).apply {
        this.onDynamicRegister = { key, value ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            val registryName = this.key().location().toString()
                            val assignedId = DynamicIdManager.getId(registryName, key.location()) ?: -1
                            val packet = DynamicRegistrySyncS2CPacket.createForTextureAdd(key.location().namespace, key.location(), value, assignedId)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        this.onDynamicUnregister = { key, _ ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            DynamicRegistrySyncS2CPacket.syncTextureRemovalToClients(key.location().namespace, key.location())
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    val JS_SCRIPTS = VirtualRegistry<OJSScript>(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_scripts"),
    ).apply {
        this.onDynamicRegister = { key, value ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            val registryName = this.key().location().toString()
                            val assignedId = DynamicIdManager.getId(registryName, key.location()) ?: -1
                            val packet = DynamicRegistrySyncS2CPacket.createForJSScriptAdd(key.location().namespace, key.location(), value, assignedId)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        this.onDynamicUnregister = { key, _ ->
            try {
                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                if (server != null) {
                    server.execute {
                        try {
                            DynamicRegistrySyncS2CPacket.syncJSScriptRemovalToClients(key.location().namespace, key.location())
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    val IK_COMPONENT_TYPE = VirtualRegistry<TypedIKComponent>(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_component_type"),
    )

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType>()
        .id("syncer_type")
        .build { it.sync(true).create() }


    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>()
        .id("sync_data_stream_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val MODEL_EDITOR_WAND = SparkCore.REGISTER.item<Item>() // Specify Item type explicitly
        .id("model_editor_wand") // 设置物品 ID
        .bound { Item(Item.Properties().rarity(Rarity.EPIC)) } // 提供 Item 实例的 Supplier
        .build() // 构建 DeferredHolder<Item, Item>

    @JvmStatic
    fun register() {}

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findRegistryByElementType(elementType: KClass<T>): VirtualRegistry<T>? {
        return SparkRegistries::class.memberProperties.firstNotNullOfOrNull { prop ->
            try {
                val instance = prop.getter.call(this)
                instance as? VirtualRegistry<T>
            } catch (e: Exception) {
                SparkCore.LOGGER.error("Error accessing registry property ${prop.name}: ${e.message}", e)
                null
            }
        }
    }
}
