package cn.solarmoon.spark_core.sync

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

class IntSyncData(
    override val data: Int
): SyncData {

    override val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out SyncData> = STREAM_CODEC

    companion object {
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, out SyncData> = StreamCodec.composite(
            ByteBufCodecs.INT, IntSyncData::data,
            ::IntSyncData
        )
    }

}