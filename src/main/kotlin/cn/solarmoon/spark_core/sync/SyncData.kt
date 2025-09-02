package cn.solarmoon.spark_core.sync

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs
import java.util.function.Function

interface SyncData {

    val data: Any

    val codec: MapCodec<out SyncData>

    val streamCodec: StreamCodec<RegistryFriendlyByteBuf, out SyncData>

    companion object {
        val CODEC = SparkRegistries.SYNC_DATA_CODEC.byNameCodec()
            .dispatch(
                SyncData::codec,
                Function.identity()
            )

        val STREAM_CODEC = NeoForgeStreamCodecs.lazy {
            ByteBufCodecs.registry(SparkRegistries.SYNC_DATA_STREAM_CODEC.key()).dispatch(
                SyncData::streamCodec,
                Function.identity()
            )
        }
    }

}