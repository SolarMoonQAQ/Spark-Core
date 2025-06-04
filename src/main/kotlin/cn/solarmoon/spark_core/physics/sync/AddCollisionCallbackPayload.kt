package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.callback.CustomnpcCollisionCallback
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional
import java.util.function.Function

/**
 * Payload to synchronize the addition of a CustomnpcCollisionCallback to a PhysicsCollisionObject.
 * Sent from server to client.
 */
data class AddCollisionCallbackPayload(
    val hostSyncerType: SyncerType,
    val hostSyncData: SyncData,
    val pcoCollisionBoxId: String, // ID of the PhysicsCollisionObject
    val callbackName: String,      // Name for the CustomnpcCollisionCallback instance
    val callbackAutoReset: Boolean,
    val callbackResetAfterTicks: Int? // Nullable, maps to resetTicks in CustomnpcCollisionCallback
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AddCollisionCallbackPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "add_collision_callback")
        )

        private val REG_SYNCER_TYPE_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncerType> =
            StreamCodec.of(SyncerType.STREAM_CODEC::encode, SyncerType.STREAM_CODEC::decode)

        private val REG_SYNC_DATA_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncData> =
            StreamCodec.of(SyncData.STREAM_CODEC::encode, SyncData.STREAM_CODEC::decode)

        private val REG_STRING_CODEC: StreamCodec<RegistryFriendlyByteBuf, String> =
            StreamCodec.of(ByteBufCodecs.STRING_UTF8::encode, ByteBufCodecs.STRING_UTF8::decode)

        private val REG_BOOL_CODEC: StreamCodec<RegistryFriendlyByteBuf, Boolean> =
            StreamCodec.of(ByteBufCodecs.BOOL::encode, ByteBufCodecs.BOOL::decode)

        private val REG_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, Int> = 
            StreamCodec.of(ByteBufCodecs.INT::encode, ByteBufCodecs.INT::decode)

        private val REG_OPTIONAL_INT_STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Optional<Int>> = 
            ByteBufCodecs.optional(REG_INT_CODEC)

        private val OPTIONAL_INT_TO_NULLABLE_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, Int?> = 
            REG_OPTIONAL_INT_STREAM_CODEC.map(
                Function<Optional<Int>, Int?> { optionalInt -> optionalInt.orElse(null) }, 
                Function<Int?, Optional<Int>> { nullableInt -> Optional.ofNullable(nullableInt) }
            )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AddCollisionCallbackPayload> = StreamCodec.composite(
            REG_SYNCER_TYPE_CODEC, AddCollisionCallbackPayload::hostSyncerType,
            REG_SYNC_DATA_CODEC, AddCollisionCallbackPayload::hostSyncData,
            REG_STRING_CODEC, AddCollisionCallbackPayload::pcoCollisionBoxId,
            REG_STRING_CODEC, AddCollisionCallbackPayload::callbackName, // callbackName is also a string
            REG_BOOL_CODEC, AddCollisionCallbackPayload::callbackAutoReset,
            OPTIONAL_INT_TO_NULLABLE_INT_CODEC, AddCollisionCallbackPayload::callbackResetAfterTicks,
            ::AddCollisionCallbackPayload
        )

        @JvmStatic
        fun handleInClient(payload: AddCollisionCallbackPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() ?: return@enqueueWork // Client-side check
                val level = player.level() ?: return@enqueueWork
                if (!level.isClientSide) {
                    SparkCore.LOGGER.warn("AddCollisionCallbackPayload received on server, ignoring.")
                    return@enqueueWork
                }

                val hostEntity = payload.hostSyncerType.getSyncer(level, payload.hostSyncData) as? Entity
                if (hostEntity == null) {
                    SparkCore.LOGGER.warn("AddCollisionCallback: Host entity not found on client for synctype: ${payload.hostSyncerType} and syncdata: ${payload.hostSyncData}")
                    return@enqueueWork
                }
                
                val physicsHost = hostEntity as? PhysicsHost
                if (physicsHost == null){
                    SparkCore.LOGGER.warn("AddCollisionCallback: Host entity ${hostEntity.id} is not a PhysicsHost.")
                    return@enqueueWork
                }

                val pco = physicsHost.getBody(payload.pcoCollisionBoxId, PhysicsCollisionObject::class) // Get as generic PCO
                if (pco == null) {
                    SparkCore.LOGGER.warn("AddCollisionCallback: PhysicsCollisionObject not found on client with id '${payload.pcoCollisionBoxId}' for host entity ${hostEntity.id}")
                    return@enqueueWork
                }

                // Check if a callback with the same name already exists to prevent duplicates if necessary
                val existingCallback = pco.collisionListeners?.find { (it as? CustomnpcCollisionCallback)?.cbName == payload.callbackName }
                if (existingCallback != null) {
                    SparkCore.LOGGER.debug("AddCollisionCallback: Callback with name '${payload.callbackName}' already exists on PCO '${payload.pcoCollisionBoxId}' for host ${hostEntity.id}. Skipping addition.")
                    return@enqueueWork
                }

                val newCallback = CustomnpcCollisionCallback(
                    cbName = payload.callbackName,
                    owner = hostEntity, // The entity that owns the PCO
                    collisionBoxId = payload.pcoCollisionBoxId, // The ID of the PCO this callback is attached to
                    autoResetEnabled = payload.callbackAutoReset,
                    resetAfterTicks = payload.callbackResetAfterTicks ?: 0 // Default to 0 if null
                )

                pco.addCollisionCallback(newCallback)

                SparkCore.LOGGER.debug(
                    "Client: Added CustomnpcCollisionCallback '{}' to PCO '{}' on entity '{}'. AutoReset: {}, ResetTicks: {}",
                    payload.callbackName,
                    payload.pcoCollisionBoxId,
                    hostEntity.id,
                    payload.callbackAutoReset,
                    payload.callbackResetAfterTicks ?: 0
                )

            }.exceptionally { e ->
                SparkCore.LOGGER.error("Error handling AddCollisionCallbackPayload on client", e)
                context.disconnect(Component.literal("Error processing add collision callback sync"))
                null
            }
        }
    }
}
