package cn.solarmoon.spark_core.entity

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class EntityMovingPayload(
    val id: Int,
    val moving: Boolean
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInBothSide(payload: EntityMovingPayload, context: IPayloadContext) {
            val player = context.player()
            val level = player.level()
            val entity = level.getEntity(payload.id) ?: return
            entity.isMoving = payload.moving
            if (!level.isClientSide) {
                PacketDistributor.sendToPlayersNear(level as ServerLevel, player as ServerPlayer, entity.x, entity.y, entity.z, 1024.0, payload)
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<EntityMovingPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "entity_moving")
        )

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EntityMovingPayload::id,
            ByteBufCodecs.BOOL, EntityMovingPayload::moving,
            ::EntityMovingPayload
        )
    }
}