package cn.solarmoon.spark_core.physics.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.callback.SparkCollisionCallback
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional
import java.util.HashSet
import java.util.function.Function

data class AttackSystemSyncPayload(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val collisionBoxId: String,
    val cbName: String,
    val attackedEntities: Set<Int>,
    val ignoreInvulnerableTime: Boolean,
    val autoResetEnabled: Boolean,
    val ticksSinceLastReset: Int,
    val resetAfterTicks: Int?
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AttackSystemSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_attack_system")
        )

        private val REG_SYNCER_TYPE_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncerType> =
            StreamCodec.of(SyncerType.STREAM_CODEC::encode, SyncerType.STREAM_CODEC::decode)

        private val REG_SYNC_DATA_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncData> =
            StreamCodec.of(SyncData.STREAM_CODEC::encode, SyncData.STREAM_CODEC::decode)
        
        private val REG_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, Int> = 
            StreamCodec.of(ByteBufCodecs.INT::encode, ByteBufCodecs.INT::decode)
        
        private val REG_BOOL_CODEC: StreamCodec<RegistryFriendlyByteBuf, Boolean> = 
            StreamCodec.of(ByteBufCodecs.BOOL::encode, ByteBufCodecs.BOOL::decode)

        private val REG_STRING_CODEC: StreamCodec<RegistryFriendlyByteBuf, String> = 
            StreamCodec.of(ByteBufCodecs.STRING_UTF8::encode, ByteBufCodecs.STRING_UTF8::decode)

        private val REG_OPTIONAL_INT_STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Optional<Int>> = 
            ByteBufCodecs.optional(REG_INT_CODEC)

        private val OPTIONAL_INT_TO_NULLABLE_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, Int?> = 
            REG_OPTIONAL_INT_STREAM_CODEC.map(
                Function<Optional<Int>, Int?> { optionalInt -> optionalInt.orElse(null) }, 
                Function<Int?, Optional<Int>> { nullableInt -> Optional.ofNullable(nullableInt) }
            )
        
        private val RAW_HASHSET_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, HashSet<Int>> = 
            ByteBufCodecs.collection({ HashSet<Int>() }, REG_INT_CODEC)

        private val SET_INT_CODEC: StreamCodec<RegistryFriendlyByteBuf, Set<Int>> =
            RAW_HASHSET_INT_CODEC.map(
                Function<HashSet<Int>, Set<Int>> { hashSet -> hashSet },
                Function<Set<Int>, HashSet<Int>> { set -> set as? HashSet<Int> ?: HashSet(set) }
            )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AttackSystemSyncPayload> = 
            object : StreamCodec<RegistryFriendlyByteBuf, AttackSystemSyncPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): AttackSystemSyncPayload {
                    val syncerType = REG_SYNCER_TYPE_CODEC.decode(buffer)
                    val syncData = REG_SYNC_DATA_CODEC.decode(buffer)
                    val collisionBoxId = REG_STRING_CODEC.decode(buffer)
                    val cbName = REG_STRING_CODEC.decode(buffer)
                    val attackedEntities = SET_INT_CODEC.decode(buffer)
                    val ignoreInvulnerableTime = REG_BOOL_CODEC.decode(buffer) // Read ignoreInvulnerableTime first
                    val autoResetEnabled = REG_BOOL_CODEC.decode(buffer)     // Then read autoResetEnabled
                    val ticksSinceLastReset = REG_INT_CODEC.decode(buffer)
                    val resetAfterTicks = OPTIONAL_INT_TO_NULLABLE_INT_CODEC.decode(buffer)
                    return AttackSystemSyncPayload(
                        syncerType, 
                        syncData, 
                        collisionBoxId,
                        cbName,
                        attackedEntities, 
                        ignoreInvulnerableTime,    // Correct order for constructor
                        autoResetEnabled,          // Correct order for constructor
                        ticksSinceLastReset, 
                        resetAfterTicks
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: AttackSystemSyncPayload) {
                    REG_SYNCER_TYPE_CODEC.encode(buffer, value.syncerType)
                    REG_SYNC_DATA_CODEC.encode(buffer, value.syncData)
                    REG_STRING_CODEC.encode(buffer, value.collisionBoxId)
                    REG_STRING_CODEC.encode(buffer, value.cbName)
                    SET_INT_CODEC.encode(buffer, value.attackedEntities)
                    REG_BOOL_CODEC.encode(buffer, value.ignoreInvulnerableTime) // Encode ignoreInvulnerableTime
                    REG_BOOL_CODEC.encode(buffer, value.autoResetEnabled)   // Encode autoResetEnabled (New)
                    REG_INT_CODEC.encode(buffer, value.ticksSinceLastReset)
                    OPTIONAL_INT_TO_NULLABLE_INT_CODEC.encode(buffer, value.resetAfterTicks)
                }
            }

        @JvmStatic
        fun handleInClient(payload: AttackSystemSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() ?: return@enqueueWork
                val level = player.level() ?: return@enqueueWork
                if (!level.isClientSide) {
                    SparkCore.LOGGER.warn("AttackSystemSyncPayload received on server, ignoring.")
                    return@enqueueWork
                }

                val host = payload.syncerType.getSyncer(level, payload.syncData) as? PhysicsHost ?: return@enqueueWork

                val body = host.getBody(payload.collisionBoxId, PhysicsRigidBody::class)
                if (body == null) {
                    // TODO: 修复该问题
                    // SparkCore.LOGGER.warn("Client: No PhysicsRigidBody found for id ${payload.collisionBoxId} on entity ${(host as? Entity)?.id}")
                    return@enqueueWork
                }

                val callbacks: List<CollisionCallback>? = body.collisionListeners
                val foundCallback = callbacks?.find { callback: CollisionCallback -> 
                    (callback as? SparkCollisionCallback)?.cbName == payload.cbName
                } as? SparkCollisionCallback
                
                if (foundCallback == null) {
                    SparkCore.LOGGER.warn("Client: No SparkCollisionCallback found with name ${payload.collisionBoxId} for collisionBoxId on entity ${(host as? Entity)?.id}, available callbacks: ${callbacks?.joinToString { (it as? Any)?.javaClass?.simpleName ?: "unknown_type" }}")
                    return@enqueueWork
                }

                val attackSystem = foundCallback.attackSystem
                
                attackSystem.ignoreInvulnerableTime = payload.ignoreInvulnerableTime
                attackSystem.internalSetTicksSinceLastReset(payload.ticksSinceLastReset)
                attackSystem.resetAfterTicks = payload.resetAfterTicks
                attackSystem.internalSetAttackedEntities(payload.attackedEntities)

//                val hostEntityId = (host as? Entity)?.id ?: "unknown_host_type"

//                SparkCore.LOGGER.debug(
//                    "Client AttackSystem for cbId '{}' on entity '{}': Synced. attacked={}, ignoreInvulnerable={}, ticksSinceReset={}, resetAfterTicks={}",
//                    payload.collisionBoxId,
//                    hostEntityId,
//                    payload.attackedEntities,
//                    payload.ignoreInvulnerableTime,
//                    payload.ticksSinceLastReset,
//                    payload.resetAfterTicks
//                )

            }.exceptionally { e ->
                SparkCore.LOGGER.error("Error handling AttackSystemSyncPayload on client", e)
                context.disconnect(Component.literal("Error processing attack system sync"))
                null
            }
        }
    }
}
