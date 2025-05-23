package cn.solarmoon.spark_core.network.payload.resource_sync

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class AnimationUpdatedPayload(
    val rootLocation: ResourceLocation,
    val changeType: ChangeType
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<AnimationUpdatedPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AnimationUpdatedPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "animation_updated")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AnimationUpdatedPayload> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, AnimationUpdatedPayload::rootLocation,
            ByteBufCodecs.VAR_INT.map( { ChangeType.entries[it] }, { it.ordinal } ), AnimationUpdatedPayload::changeType,
            ::AnimationUpdatedPayload
        )

        fun handleInClient(payload: AnimationUpdatedPayload, context: IPayloadContext) {
            context.enqueueWork {
                // Client-side handling
                SparkCore.LOGGER.info("Received AnimationUpdatedPayload: ${payload.rootLocation} - ${payload.changeType}")
                // TODO: Implement actual client-side refresh logic for animations
                // For example, find entities using this animation root and refresh their anim controller,
                // or clear cached client-side animation data if any.
            }
        }
    }
}
