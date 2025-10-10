package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimController
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class AnimStopPayload private constructor(
    val syncerType: SyncerType<*>,
    val syncData: SyncData,
    val layerId: Int,
): CustomPacketPayload {
    constructor(syncer: Syncer, layerId: Int): this(syncer.syncerType, syncer.syncData, layerId)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleBothSide(payload: AnimStopPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = payload.syncerType.getSyncer(level, payload.syncData)
            if (entity !is IAnimatable<*>) return
            val controller = entity.animController
            controller.stopAnimation(payload.layerId)
            // 从单人客户端发来的同步需要再同步给其它玩家客户端
            if (!level.isClientSide) PacketDistributor.sendToPlayersInDimension(level as ServerLevel, payload)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimStopPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "anim_stop"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, AnimStopPayload::syncerType,
            SyncData.STREAM_CODEC, AnimStopPayload::syncData,
            ByteBufCodecs.INT, AnimStopPayload::layerId,
            ::AnimStopPayload
        )
    }

}