package cn.solarmoon.spark_core.network.payload.resource_sync

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class ModelUpdatedPayload(
    val rootLocation: ResourceLocation,
    val changeType: ChangeType
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ModelUpdatedPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ModelUpdatedPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "model_updated")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ModelUpdatedPayload> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModelUpdatedPayload::rootLocation,
            ByteBufCodecs.VAR_INT.map( { ChangeType.entries[it] }, { it.ordinal } ), ModelUpdatedPayload::changeType,
            ::ModelUpdatedPayload
        )

        fun handleInClient(payload: ModelUpdatedPayload, context: IPayloadContext) {
            context.enqueueWork {
                // Client-side handling
                SparkCore.LOGGER.info("Received ModelUpdatedPayload: ${payload.rootLocation} - ${payload.changeType}")
                // TODO: Implement actual client-side refresh logic for models
                // For example, find entities using this model root and mark them for re-render or model data refresh.
            }
        }
    }
}
