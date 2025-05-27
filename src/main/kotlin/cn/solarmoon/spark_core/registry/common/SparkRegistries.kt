package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.payload.registry.DynamicRegistrySyncS2CPacket
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
                    val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimationAdd(key.location(), value)
                    net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
                    SparkCore.LOGGER.info("Triggered dynamic TypedAnimation ADD sync for ${key.location()} via callback")
                }
                this.onDynamicUnregister = { key, value ->
                    DynamicRegistrySyncS2CPacket.syncTypedAnimationRemovalToClients(key.location())
                    SparkCore.LOGGER.info("Triggered dynamic TypedAnimation REMOVAL sync for ${key.location()} via callback. Animation: $value")
                }
            } ?: throw IllegalStateException("TYPED_ANIMATION registry could not be cast to DynamicAwareRegistry. Check ObjectRegister implementation.")

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType>()
        .id("syncer_type")
        .valueType(SyncerType::class) 
        .build { it.sync(true).create() }

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
    val MODELS = SparkCore.REGISTER.registry<OModel>()
        .id("models")
        .valueType(OModel::class)
        .build { it.sync(true).create() }

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