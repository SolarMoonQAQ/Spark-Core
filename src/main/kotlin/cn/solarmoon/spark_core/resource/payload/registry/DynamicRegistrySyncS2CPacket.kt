package cn.solarmoon.spark_core.resource.payload.registry

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.animation.texture.OTexture
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
enum class OperationType {
    ADD, REMOVE
}

data class DynamicRegistrySyncS2CPacket(
    val modId: String,
    val registryKey: ResourceKey<out Registry<*>>,
    val entryId: ResourceLocation,
    val operationType: OperationType,
    val entryData: ByteArray
) : CustomPacketPayload {

    companion object {
        @JvmField
        val ID = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "dynamic_registry_sync")

        @JvmField
        val TYPE = CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket>(ID)

        @JvmField
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, DynamicRegistrySyncS2CPacket> {
            override fun encode(buf: FriendlyByteBuf, packet: DynamicRegistrySyncS2CPacket) {
                buf.writeUtf(packet.modId)
                buf.writeResourceLocation(packet.registryKey.location())
                buf.writeResourceLocation(packet.entryId)
                buf.writeEnum(packet.operationType)
                buf.writeByteArray(packet.entryData)
            }

            override fun decode(buf: FriendlyByteBuf): DynamicRegistrySyncS2CPacket {
                val modId = buf.readUtf()
                val registryKeyLocation = buf.readResourceLocation()
                val registryKey = ResourceKey.createRegistryKey<Registry<*>>(registryKeyLocation)
                val entryId = buf.readResourceLocation()
                val operationType = buf.readEnum(OperationType::class.java)
                val entryData = buf.readByteArray()
                return DynamicRegistrySyncS2CPacket(modId,registryKey, entryId, operationType, entryData)
            }
        }

        fun createForTypedAnimationAdd(modId: String,id: ResourceLocation, animation: TypedAnimation): DynamicRegistrySyncS2CPacket {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeResourceLocation(animation.index.index)
            buf.writeUtf(animation.index.name)

            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release()

            return DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.TYPED_ANIMATION.key(),
                id,
                OperationType.ADD,
                bytes
            )
        }

        fun createForModelAdd(modId: String, id: ResourceLocation, model: OModel): DynamicRegistrySyncS2CPacket {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            OModel.STREAM_CODEC.encode(buf, model)

            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release()

            return DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.MODELS.key(),
                id,
                OperationType.ADD,
                bytes
            )
        }

        fun createForJSScriptAdd(modId: String, id: ResourceLocation, script: OJSScript): DynamicRegistrySyncS2CPacket {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            OJSScript.STREAM_CODEC.encode(buf, script)

            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release()

            return DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.JS_SCRIPTS.key(),
                id,
                OperationType.ADD,
                bytes
            )
        }

        fun createForTextureAdd(modId: String, id: ResourceLocation, texture: OTexture): DynamicRegistrySyncS2CPacket {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            OTexture.STREAM_CODEC.encode(buf, texture)

            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release()

            return DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.DYNAMIC_TEXTURES.key(),
                id,
                OperationType.ADD,
                bytes
            )
        }

        fun syncTypedAnimationRemovalToClients(modId: String, id: ResourceLocation) {
            val packet = DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.TYPED_ANIMATION.key(),
                id,
                OperationType.REMOVE,
                ByteArray(0)
            )
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic TypedAnimation REMOVAL sync packet for $id to all clients")
        }

        fun syncModelRemovalToClients(modId: String, id: ResourceLocation) {
            val packet = DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.MODELS.key(),
                id,
                OperationType.REMOVE,
                ByteArray(0)
            )
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic OModel REMOVAL sync packet for $id to all clients")
        }

        fun syncJSScriptRemovalToClients(modId: String, id: ResourceLocation) {
            val packet = DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.JS_SCRIPTS.key(),
                id,
                OperationType.REMOVE,
                ByteArray(0)
            )
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic OJSScript REMOVAL sync packet for $id to all clients")
        }

        fun syncTextureRemovalToClients(modId: String, id: ResourceLocation) {
            val packet = DynamicRegistrySyncS2CPacket(
                modId,
                SparkRegistries.DYNAMIC_TEXTURES.key(),
                id,
                OperationType.REMOVE,
                ByteArray(0)
            )
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic OTexture REMOVAL sync packet for $id to all clients")
        }

        fun handleInClient(packet: DynamicRegistrySyncS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
                    when (packet.registryKey) {
                        SparkRegistries.TYPED_ANIMATION.key() -> {
                            handleTypedAnimationSync(packet)
                        }
                        SparkRegistries.MODELS.key() -> {
                            handleModelSync(packet)
                        }
                        SparkRegistries.JS_SCRIPTS.key() -> {
                            handleJSScriptSync(packet)
                        }
                        SparkRegistries.DYNAMIC_TEXTURES.key() -> {
                            handleTextureSync(packet)
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
                    // 检查是否已存在，避免重复注册导致循环发包
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

                    // 使用不触发回调的注册方式，避免客户端触发服务端回调
                    dynamicRegistry.registerDynamic(packet.entryId, animation, packet.modId, replace = false, triggerCallback = false)
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

        private fun handleModelSync(packet: DynamicRegistrySyncS2CPacket) {
            val dynamicRegistry = SparkRegistries.MODELS

            when (packet.operationType) {
                OperationType.ADD -> {
                    // 检查是否已存在，避免重复注册导致循环发包
                    if (dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OModel {} already exists, skipping ADD sync", packet.entryId)
                        return
                    }

                    val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(packet.entryData))
                    val model = OModel.STREAM_CODEC.decode(buf)
                    buf.release()

                    // 使用不触发回调的注册方式，避免客户端触发服务端回调
                    dynamicRegistry.registerDynamic(packet.entryId, model, packet.modId, replace = false)
                    SparkCore.LOGGER.info("Dynamically registered OModel ${packet.entryId} from server")
                }
                OperationType.REMOVE -> {
                    if (!dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OModel {} does not exist, skipping REMOVE sync", packet.entryId)
                        return
                    }
                    dynamicRegistry.unregisterDynamic(packet.entryId)
                    SparkCore.LOGGER.info("Dynamically unregistered OModel ${packet.entryId} from server")
                }
            }
        }

        private fun handleJSScriptSync(packet: DynamicRegistrySyncS2CPacket) {
            val dynamicRegistry = SparkRegistries.JS_SCRIPTS

            when (packet.operationType) {
                OperationType.ADD -> {
                    // 检查是否已存在，避免重复注册导致循环发包
                    if (dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OJSScript {} already exists, skipping ADD sync", packet.entryId)
                        return
                    }

                    val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(packet.entryData))
                    val script = OJSScript.STREAM_CODEC.decode(buf)
                    buf.release()

                    // 使用不触发回调的注册方式，避免客户端触发服务端回调
                    dynamicRegistry.registerDynamic(packet.entryId, script, packet.modId, replace = false)
                    SparkCore.LOGGER.info("Dynamically registered OJSScript ${packet.entryId} from server")
                }
                OperationType.REMOVE -> {
                    if (!dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OJSScript {} does not exist, skipping REMOVE sync", packet.entryId)
                        return
                    }
                    dynamicRegistry.unregisterDynamic(packet.entryId)
                    SparkCore.LOGGER.info("Dynamically unregistered OJSScript ${packet.entryId} from server")
                }
            }
        }

        private fun handleTextureSync(packet: DynamicRegistrySyncS2CPacket) {
            val dynamicRegistry = SparkRegistries.DYNAMIC_TEXTURES

            when (packet.operationType) {
                OperationType.ADD -> {
                    // 检查是否已存在，避免重复注册导致循环发包
                    if (dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OTexture {} already exists, skipping ADD sync", packet.entryId)
                        return
                    }

                    val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(packet.entryData))
                    val texture = OTexture.STREAM_CODEC.decode(buf)
                    buf.release()

                    // 使用不触发回调的注册方式，避免客户端触发服务端回调
                    dynamicRegistry.registerDynamic(packet.entryId, texture, packet.modId, replace = false)
                    SparkCore.LOGGER.info("Dynamically registered OTexture ${packet.entryId} from server")
                }
                OperationType.REMOVE -> {
                    if (!dynamicRegistry.containsKey(packet.entryId)) {
                        SparkCore.LOGGER.debug("OTexture {} does not exist, skipping REMOVE sync", packet.entryId)
                        return
                    }
                    dynamicRegistry.unregisterDynamic(packet.entryId)
                    SparkCore.LOGGER.info("Dynamically unregistered OTexture ${packet.entryId} from server")
                }
            }
        }

        @Deprecated("Use createForTypedAnimationAdd and send manually or a new specific sync method", ReplaceWith("createForTypedAnimationAdd"))
        private fun syncTypedAnimationToClients(modId: String, id: ResourceLocation, animation: TypedAnimation) {
            val packet = createForTypedAnimationAdd(modId, id, animation)
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent dynamic TypedAnimation ADD sync packet for $id to all clients")
        }
    }

    fun id(): ResourceLocation = ID

    override fun type(): CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket> {
        return TYPE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DynamicRegistrySyncS2CPacket

        if (registryKey != other.registryKey) return false
        if (entryId != other.entryId) return false
        if (operationType != other.operationType) return false
        if (!entryData.contentEquals(other.entryData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = registryKey.hashCode()
        result = 31 * result + entryId.hashCode()
        result = 31 * result + operationType.hashCode()
        result = 31 * result + entryData.contentHashCode()
        return result
    }
}
