package cn.solarmoon.spark_core.sync

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class BlockPosSyncData(
    override val data: BlockPos
): SyncData {

    override val codec: MapCodec<out SyncData>
        get() = CODEC
    override val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out SyncData>
        get() = STREAM_CODEC

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec {
            it.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(BlockPosSyncData::data)
            ).apply(it, ::BlockPosSyncData)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, out SyncData> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockPosSyncData::data,
            ::BlockPosSyncData
        )
    }
}