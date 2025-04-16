package cn.solarmoon.spark_core.ik.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.ik.component.IKHost

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional

/**
 * Payload sent from Server -> Client to synchronize the authoritative IK target position for a chain.
 */
class IKSyncTargetPayload(
    val targetEntityId: Int,
    val chainName: String,
    val targetPosition: Vec3? // Nullable Vec3: null means clear the target
) : CustomPacketPayload {

    // Convenience constructor (optional, but can be useful)
    constructor(host: IKHost<*>, chainName: String, targetPosition: Vec3?) : this(
        (host as? net.minecraft.world.entity.Entity)?.id ?: -1, // Safely get entity ID
        chainName,
        targetPosition
    )

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<IKSyncTargetPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_ik_target"))

        @JvmStatic
        // Codec
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, IKSyncTargetPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, { it.targetEntityId },
            ByteBufCodecs.STRING_UTF8, { it.chainName },
            ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC), { Optional.ofNullable(it.targetPosition) },
            { targetEntityId, chainName, targetPosition -> IKSyncTargetPayload(targetEntityId, chainName, targetPosition.orElse(null)) }
        )

        @JvmStatic
        // Handler executed on the Client thread
        fun handleInClient(payload: IKSyncTargetPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork
                val targetEntity = level.getEntity(payload.targetEntityId)

                if (targetEntity == null) {
                    // Entity might not exist on client yet or was removed
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: Target entity ${payload.targetEntityId} not found on client.")
                    return@enqueueWork
                }

                val host = targetEntity as? IKHost<*> ?: return@enqueueWork // Check if it's an IKHost

                // Update the client's target map based on the received authoritative state
                if (payload.targetPosition != null) {
                    host.ikTargetPositions[payload.chainName] = payload.targetPosition
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: Set client target for chain '${payload.chainName}' on entity ${payload.targetEntityId}")
                } else {
                    host.ikTargetPositions.remove(payload.chainName)
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: Cleared client target for chain '${payload.chainName}' on entity ${payload.targetEntityId}")
                }
            }.exceptionally {
                SparkCore.LOGGER.error("Exception handling IKSyncTargetPayload", it)
                null
            }
        }
    }
}