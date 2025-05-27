package cn.solarmoon.spark_core.resource.payload.registry

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import io.netty.buffer.Unpooled
import net.minecraft.core.Registry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 动态注册表同步数据包（服务端到客户端）
 *
 * 用于将服务端动态注册的条目同步到客户端
 */
class DynamicRegistrySyncS2CPacket(
    val registryKey: ResourceKey<*>,
    val entryId: ResourceLocation,
    val operationType: OperationType,
    val entryData: ByteArray
) : CustomPacketPayload {

    enum class OperationType {
        ADD,
        REMOVE
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "dynamic_registry_sync")
        val TYPE = CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket>(ID)

        val CODEC = object : StreamCodec<FriendlyByteBuf, DynamicRegistrySyncS2CPacket> {
            override fun encode(buf: FriendlyByteBuf, packet: DynamicRegistrySyncS2CPacket) {
                buf.writeResourceLocation(packet.registryKey.location())
                buf.writeResourceLocation(packet.entryId)
                buf.writeEnum(packet.operationType)
                buf.writeByteArray(packet.entryData)
            }

            override fun decode(buf: FriendlyByteBuf): DynamicRegistrySyncS2CPacket {
                val registryKeyLocation = buf.readResourceLocation()
                val registryKey = ResourceKey.createRegistryKey<Registry<*>>(registryKeyLocation)
                val entryId = buf.readResourceLocation()
                val operationType = buf.readEnum(OperationType::class.java)
                val entryData = buf.readByteArray()
                return DynamicRegistrySyncS2CPacket(registryKey, entryId, operationType, entryData)
            }
        }

        fun createForTypedAnimationAdd(id: ResourceLocation, animation: TypedAnimation): DynamicRegistrySyncS2CPacket {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeResourceLocation(animation.index.index)
            buf.writeUtf(animation.index.name)

            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release()

            return DynamicRegistrySyncS2CPacket(
                SparkRegistries.TYPED_ANIMATION.key(),
                id,
                OperationType.ADD,
                bytes
            )
        }

        fun syncTypedAnimationRemovalToClients(id: ResourceLocation) {
            val packet = DynamicRegistrySyncS2CPacket(
                SparkRegistries.TYPED_ANIMATION.key(),
                id,
                OperationType.REMOVE,
                ByteArray(0)
            )
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic TypedAnimation REMOVAL sync packet for $id to all clients")
        }

        fun handleInClient(packet: DynamicRegistrySyncS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
                    when (packet.registryKey) {
                        SparkRegistries.TYPED_ANIMATION.key() -> {
                            handleTypedAnimationSync(packet)
                        }
                        else -> {
                            SparkCore.LOGGER.warn("Received dynamic registry sync for unknown registry: ${packet.registryKey}")
                        }
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Error handling dynamic registry sync packet", e)
                }
            }.exceptionally { 
                SparkCore.LOGGER.error("处理 DynamicRegistrySyncS2CPacket 时发生异常", it)
                null
            }
        }

        private fun handleTypedAnimationSync(packet: DynamicRegistrySyncS2CPacket) {
            val dynamicRegistry = SparkRegistries.TYPED_ANIMATION

            when (packet.operationType) {
                OperationType.ADD -> {
                    if (dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("TypedAnimation {} already exists, skipping ADD sync", packet.entryId)
                        return
                    }

                    val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(packet.entryData))
                    val indexLocation = buf.readResourceLocation()
                    val animName = buf.readUtf()
                    buf.release()

                    val animIndex = AnimIndex(indexLocation, animName)
                    val animation = TypedAnimation(animIndex) {}

                    dynamicRegistry.registerDynamic(packet.entryId, animation)
                    SparkCore.LOGGER.info("Dynamically registered TypedAnimation ${packet.entryId} from server")
                }
                OperationType.REMOVE -> {
                    if (!dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("TypedAnimation {} does not exist, skipping REMOVE sync", packet.entryId)
                        return
                    }
                    dynamicRegistry.unregisterDynamic(packet.entryId)
                    SparkCore.LOGGER.info("Dynamically unregistered TypedAnimation ${packet.entryId} from server")
                }
            }
        }

        @Deprecated("Use createForTypedAnimationAdd and send manually or a new specific sync method", ReplaceWith("createForTypedAnimationAdd"))
        private fun syncTypedAnimationToClients(id: ResourceLocation, animation: TypedAnimation) {
            val packet = createForTypedAnimationAdd(id, animation)
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic TypedAnimation ADD sync packet for $id to all clients")
        }
    }

    fun id(): ResourceLocation = ID

    override fun type(): CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket> {
        return TYPE
    }
}
