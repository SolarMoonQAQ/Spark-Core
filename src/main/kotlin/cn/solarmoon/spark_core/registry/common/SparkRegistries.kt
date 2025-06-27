package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.payload.registry.DynamicRegistrySyncS2CPacket
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

object SparkRegistries {

    @JvmStatic
    val TYPED_ANIMATION =
        (SparkCore.REGISTER.registry<TypedAnimation>()
            .id("typed_animation")
            .valueType(TypedAnimation::class) // Pass KClass for DynamicAwareRegistry
            .build { it.sync(true).create() } as? DynamicAwareRegistry<TypedAnimation>)
            ?.apply {
                // 'this' is now safely cast to DynamicAwareRegistry<TypedAnimation>
                this.onDynamicRegister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                    val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimationAdd(key.location(), value)
                    net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                    SparkCore.LOGGER.info("Triggered dynamic TypedAnimation ADD sync for ${key.location()} via callback")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过动画ADD同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("动画ADD同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
                this.onDynamicUnregister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                    DynamicRegistrySyncS2CPacket.syncTypedAnimationRemovalToClients(key.location())
                    SparkCore.LOGGER.info("Triggered dynamic TypedAnimation REMOVAL sync for ${key.location()} via callback. Animation: $value")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过动画REMOVAL同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("动画REMOVAL同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
            } ?: throw IllegalStateException("TYPED_ANIMATION registry could not be cast to DynamicAwareRegistry. Check ObjectRegister implementation.")

    @JvmStatic
    val MODELS =
        (SparkCore.REGISTER.registry<OModel>()
            .id("models")
            .valueType(OModel::class) // Pass KClass for DynamicAwareRegistry
            .build { it.sync(true).create() } as? DynamicAwareRegistry<OModel>)
            ?.apply {
                // 'this' is now safely cast to DynamicAwareRegistry<OModel>
                this.onDynamicRegister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            val packet = DynamicRegistrySyncS2CPacket.createForModelAdd(key.location(), value)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                            SparkCore.LOGGER.info("Triggered dynamic OModel ADD sync for ${key.location()} via callback")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过模型ADD同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("模型ADD同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
                this.onDynamicUnregister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            DynamicRegistrySyncS2CPacket.syncModelRemovalToClients(key.location())
                            SparkCore.LOGGER.info("Triggered dynamic OModel REMOVAL sync for ${key.location()} via callback. Model: $value")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过模型REMOVAL同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("模型REMOVAL同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
            } ?: throw IllegalStateException("MODELS registry could not be cast to DynamicAwareRegistry. Check ObjectRegister implementation.")

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType>()
        .id("syncer_type")
        .valueType(SyncerType::class) 
        .build { it.sync(true).create() }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>()
        .id("sync_data_stream_codec")
        .valueType(StreamCodec::class as KClass<out StreamCodec<RegistryFriendlyByteBuf, out SyncData>>) 
        .build { it.sync(true).create() }

    @JvmStatic
    val IK_COMPONENT_TYPE = SparkCore.REGISTER.registry<TypedIKComponent>()
        .id("ik_component_type") // Registry name
        .valueType(TypedIKComponent::class) // Pass KClass for DynamicAwareRegistry
        .build { it.sync(true).create() } // Sync to client if needed

    @JvmStatic
    val JS_SCRIPTS =
        (SparkCore.REGISTER.registry<OJSScript>()
            .id("js_scripts")
            .valueType(OJSScript::class)
            .build { it.sync(true).create() } as? DynamicAwareRegistry<OJSScript>)
            ?.apply {
                this.onDynamicRegister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            val packet = DynamicRegistrySyncS2CPacket.createForJSScriptAdd(key.location(), value)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                            SparkCore.LOGGER.info("Triggered dynamic OJSScript ADD sync for ${key.location()} via callback")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过JS脚本ADD同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("JS脚本ADD同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
                this.onDynamicUnregister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            DynamicRegistrySyncS2CPacket.syncJSScriptRemovalToClients(key.location())
                            SparkCore.LOGGER.info("Triggered dynamic OJSScript REMOVAL sync for ${key.location()} via callback. Script: $value")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过JS脚本REMOVAL同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("JS脚本REMOVAL同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
            } ?: throw IllegalStateException("JS_SCRIPTS registry could not be cast to DynamicAwareRegistry. Check ObjectRegister implementation.")

    @JvmStatic
    val DYNAMIC_TEXTURES =
        (SparkCore.REGISTER.registry<OTexture>()
            .id("dynamic_textures")
            .valueType(OTexture::class)
            .build { it.sync(true).create() } as? DynamicAwareRegistry<OTexture>)
            ?.apply {
                this.onDynamicRegister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            val packet = DynamicRegistrySyncS2CPacket.createForTextureAdd(key.location(), value)
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                            SparkCore.LOGGER.info("Triggered dynamic OTexture ADD sync for ${key.location()} via callback")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过纹理ADD同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("纹理ADD同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
                this.onDynamicUnregister = { key, value ->
                    // 只在服务端发送同步包，避免客户端发送clientbound包错误
                    try {
                        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                        if (server != null) {
                            DynamicRegistrySyncS2CPacket.syncTextureRemovalToClients(key.location())
                            SparkCore.LOGGER.info("Triggered dynamic OTexture REMOVAL sync for ${key.location()} via callback. Texture: $value")
                        } else {
                            SparkCore.LOGGER.debug("客户端跳过纹理REMOVAL同步，等待服务端同步: ${key.location()}")
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("纹理REMOVAL同步跳过（可能在客户端）: ${key.location()}")
                    }
                }
            } ?: throw IllegalStateException("DYNAMIC_TEXTURES registry could not be cast to DynamicAwareRegistry. Check ObjectRegister implementation.")

    @JvmStatic
    val MODEL_EDITOR_WAND = SparkCore.REGISTER.item<Item>() // Specify Item type explicitly
        .id("model_editor_wand") // 设置物品 ID
        .bound { Item(Item.Properties().rarity(Rarity.EPIC)) } // 提供 Item 实例的 Supplier
        .build() // 构建 DeferredHolder<Item, Item>

    @JvmStatic
    fun register() {}

    /**
     * Finds a DynamicAwareRegistry instance within SparkRegistries that manages elements of the specified KClass type.
     *
     * @param T The type of the element the registry should manage.
     * @param elementType The KClass of the element type.
     * @return The matching DynamicAwareRegistry<T> if found, otherwise null.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findRegistryByElementType(elementType: KClass<T>): DynamicAwareRegistry<T>? {
        return SparkRegistries::class.memberProperties
            .filter { it.returnType.classifier == DynamicAwareRegistry::class }.firstNotNullOfOrNull { prop ->
                try {
                    val registryInstance = prop.getter.call(this) as? DynamicAwareRegistry<*>
                    if (registryInstance?.valueType == elementType) {
                        registryInstance as? DynamicAwareRegistry<T>
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Error accessing registry property ${prop.name}: ${e.message}", e)
                    null
                }
            }
    }
}