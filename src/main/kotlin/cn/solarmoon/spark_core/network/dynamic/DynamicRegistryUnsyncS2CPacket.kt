package cn.solarmoon.spark_core.network.dynamic

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

data class DynamicRegistryUnsyncS2CPacket(
    val registryKeyLocation: ResourceLocation, // Store ResourceLocation of the registry's key
    val entryId: ResourceLocation
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DynamicRegistryUnsyncS2CPacket> = TYPE

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "dynamic_registry_unsync")
        val TYPE = CustomPacketPayload.Type<DynamicRegistryUnsyncS2CPacket>(ID)

        val CODEC: StreamCodec<FriendlyByteBuf, DynamicRegistryUnsyncS2CPacket> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, DynamicRegistryUnsyncS2CPacket::registryKeyLocation,
            ResourceLocation.STREAM_CODEC, DynamicRegistryUnsyncS2CPacket::entryId,
            ::DynamicRegistryUnsyncS2CPacket
        )
    }
}
