package cn.solarmoon.spark_core.ik.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.caliko.IKResolver
import cn.solarmoon.spark_core.ik.component.IKHost
import cn.solarmoon.spark_core.ik.component.IKComponentType
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf // Ensure correct import if needed
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import org.slf4j.LoggerFactory

/**
 * Payload used to synchronize the active state of an IK component between server and client.
 * Typically sent Server -> Client.
 */
class IKComponentSyncPayload private constructor(
    val syncerType: SyncerType,       // Identifies the host entity type
    val syncData: SyncData,         // Identifies the specific host entity instance
    val componentTypeId: ResourceLocation, // The ID of the IKComponentType being added/removed
    val active: Boolean             // True if the component should be active, false if inactive (removed)
) : CustomPacketPayload {
    // Convenience constructor
    constructor(host: IKHost<*>, type: IKComponentType, active: Boolean) : this(
        host.syncerType, // Assuming IKHost provides these (inherited from IAnimatable/Syncer?)
        host.syncData,   // Assuming IKHost provides these
        type.id,
        active
    )

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<IKComponentSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_component_sync"))

        @JvmStatic
        // Use RegistryFriendlyByteBuf if SyncData or SyncerType codecs require it
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, IKComponentSyncPayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, IKComponentSyncPayload::syncerType,
            SyncData.STREAM_CODEC, IKComponentSyncPayload::syncData,
            ResourceLocation.STREAM_CODEC, IKComponentSyncPayload::componentTypeId,
            ByteBufCodecs.BOOL, IKComponentSyncPayload::active,
            ::IKComponentSyncPayload
        )

        @JvmStatic
        // Handler executed on the client thread
        fun handleInClient(payload: IKComponentSyncPayload, context: IPayloadContext) {
            // Ensure execution on the main client thread
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork // Use safe call for player
                // Find the host entity using the syncer info
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? IKHost<*> ?: return@enqueueWork

                // Look up the component type from the registry
                val componentType = SparkRegistries.IK_COMPONENT_TYPE.get(payload.componentTypeId) ?: run { // Access registry via .get()
                    return@enqueueWork
                }

                // Add or remove the component on the client-side manager
                if (payload.active) {
                    host.ikManager.addComponent(componentType) // Manager handles duplicates and chain building
                } else {
                    host.ikManager.removeComponent(componentType.chainName) // Remove using chainName
                }
            }.exceptionally { // Add error handling for the enqueued task
                null
            }
        }
    }
}