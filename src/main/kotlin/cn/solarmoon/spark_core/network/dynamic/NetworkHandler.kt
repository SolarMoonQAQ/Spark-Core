package cn.solarmoon.spark_core.network.dynamic

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler
import net.neoforged.neoforge.network.registration.PayloadRegistrar

/**
 * 动态注册网络处理器
 */
object NetworkHandler {

    /**
     * 注册网络处理器
     *
     * @param event 注册事件
     */
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("spark_core")
            .versioned("1.0.0")
            .optional()

        // 注册服务端到客户端的动态注册表同步数据包
        registrar.playToClient(
            DynamicRegistrySyncS2CPacket.TYPE,
            DynamicRegistrySyncS2CPacket.CODEC,
            IPayloadHandler { packet, context -> handleDynamicRegistrySyncS2C(packet, context) }
        )
    }

    /**
     * 处理动态注册表同步数据包（客户端）
     *
     * @param packet 数据包
     * @param context 上下文
     */
    private fun handleDynamicRegistrySyncS2C(packet: DynamicRegistrySyncS2CPacket, context: IPayloadContext) {
        // 确保在客户端线程上执行
        Minecraft.getInstance().execute {
            try {
                when (packet.registryKey) {
                    SparkRegistries.TYPED_ANIMATION.key() -> {
                        handleTypedAnimationSync(packet)
                    }
                    // 可以添加其他注册表的处理
                    else -> {
                        SparkCore.LOGGER.warn("Received dynamic registry sync for unknown registry: ${packet.registryKey}")
                    }
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("Error handling dynamic registry sync packet", e)
            }
        }
    }

    /**
     * 处理 TypedAnimation 同步
     *
     * @param packet 数据包
     */
    private fun handleTypedAnimationSync(packet: DynamicRegistrySyncS2CPacket) {
        val registry = SparkRegistries.TYPED_ANIMATION

        // 检查是否已存在
        if (registry.containsKey(packet.entryId)) {
            SparkCore.LOGGER.debug("TypedAnimation ${packet.entryId} already exists, skipping sync")
            return
        }

        // 解析数据
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(packet.entryData))
        val indexLocation = buf.readResourceLocation()
        val animName = buf.readUtf()
        buf.release() // 释放缓冲区

        // 创建 AnimIndex
        val animIndex = AnimIndex(indexLocation, animName)

        // 创建 TypedAnimation
        val animation = TypedAnimation(animIndex) {}

        // 注册到客户端的动态注册表
        if (registry is DynamicAwareRegistry<*>) {
            @Suppress("UNCHECKED_CAST")
            (registry as DynamicAwareRegistry<TypedAnimation>).registerDynamic(packet.entryId, animation)
            SparkCore.LOGGER.info("Dynamically registered TypedAnimation ${packet.entryId} from server")
        } else {
            SparkCore.LOGGER.error("Failed to register dynamic TypedAnimation: registry is not DynamicAwareRegistry")
        }
    }

    /**
     * 向所有客户端同步动态注册的 TypedAnimation
     *
     * @param id 动画ID
     * @param animation 动画实例
     */
    fun syncTypedAnimationToClients(id: ResourceLocation, animation: TypedAnimation) {
        val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimation(id, animation)
        PacketDistributor.sendToAllPlayers(packet)
        SparkCore.LOGGER.info("Sent dynamic TypedAnimation sync packet for ${id} to all clients")
    }
}
