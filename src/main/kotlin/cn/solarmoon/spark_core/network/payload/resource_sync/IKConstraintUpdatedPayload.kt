package cn.solarmoon.spark_core.network.payload.resource_sync

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class IKConstraintUpdatedPayload(
    val rootLocation: ResourceLocation,
    val changeType: ChangeType
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<IKConstraintUpdatedPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<IKConstraintUpdatedPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_constraint_updated")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, IKConstraintUpdatedPayload> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, IKConstraintUpdatedPayload::rootLocation,
            ByteBufCodecs.VAR_INT.map( { ChangeType.entries[it] }, { it.ordinal } ), IKConstraintUpdatedPayload::changeType,
            ::IKConstraintUpdatedPayload
        )

        fun handleInClient(payload: IKConstraintUpdatedPayload, context: IPayloadContext) {
            context.enqueueWork {
                // Client-side handling
                SparkCore.LOGGER.info("Received IKConstraintUpdatedPayload: ${payload.rootLocation} - ${payload.changeType}")
                // TODO: Implement actual client-side refresh logic for IK constraints
                // For example, find IK systems using this constraint root and re-initialize them.
            }
        }
    }
}
