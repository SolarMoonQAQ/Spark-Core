package cn.solarmoon.spark_core.sync

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

class IntSyncData(
    override val data: Int
): SyncData {

    override val codec: MapCodec<out SyncData> = CODEC

    override val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out SyncData> = STREAM_CODEC

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.fieldOf("data").forGetter(IntSyncData::data)
            ).apply(it, ::IntSyncData)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, out SyncData> = StreamCodec.composite(
            ByteBufCodecs.INT, IntSyncData::data,
            ::IntSyncData
        )
    }

}