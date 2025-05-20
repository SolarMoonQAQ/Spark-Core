package cn.solarmoon.spark_core.network.dynamic

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
import java.util.function.Consumer

/**
 * 动态注册表同步数据包（服务端到客户端）
 *
 * 用于将服务端动态注册的条目同步到客户端
 */
class DynamicRegistrySyncS2CPacket(
    val registryKey: ResourceKey<*>,
    val entryId: ResourceLocation,
    val entryData: ByteArray
) : CustomPacketPayload {

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath("spark_core", "dynamic_registry_sync")
        val TYPE = CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket>(ID)

        val CODEC = object : StreamCodec<FriendlyByteBuf, DynamicRegistrySyncS2CPacket> {
            override fun encode(buf: FriendlyByteBuf, packet: DynamicRegistrySyncS2CPacket) {
                // 写入注册表键
                buf.writeResourceLocation(packet.registryKey.location())
                // 写入条目ID
                buf.writeResourceLocation(packet.entryId)
                // 写入条目数据
                buf.writeByteArray(packet.entryData)
            }

            override fun decode(buf: FriendlyByteBuf): DynamicRegistrySyncS2CPacket {
                // 读取注册表键
                val registryKeyLocation = buf.readResourceLocation()
                val registryKey = ResourceKey.createRegistryKey<Registry<*>>(registryKeyLocation)
                // 读取条目ID
                val entryId = buf.readResourceLocation()
                // 读取条目数据
                val entryData = buf.readByteArray()
                return DynamicRegistrySyncS2CPacket(registryKey, entryId, entryData)
            }
        }

        /**
         * 创建 TypedAnimation 同步数据包
         *
         * @param id 动画的资源位置
         * @param animation 要同步的动画
         * @return 同步数据包
         */
        fun createForTypedAnimation(id: ResourceLocation, animation: TypedAnimation): DynamicRegistrySyncS2CPacket {
            // 目前简单实现，只传输 AnimIndex 和 provider 的引用
            // 实际应用中可能需要更复杂的序列化机制
            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeResourceLocation(animation.index.index)
            buf.writeUtf(animation.index.name)

            // 获取字节数组
            val bytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, bytes)
            buf.release() // 释放缓冲区

            return DynamicRegistrySyncS2CPacket(
                SparkRegistries.TYPED_ANIMATION.key(),
                id,
                bytes
            )
        }
    }

    fun id(): ResourceLocation = ID

    fun write(buf: FriendlyByteBuf) {
        CODEC.encode(buf, this)
    }

    override fun type(): CustomPacketPayload.Type<DynamicRegistrySyncS2CPacket> {
        return TYPE
    }
}
