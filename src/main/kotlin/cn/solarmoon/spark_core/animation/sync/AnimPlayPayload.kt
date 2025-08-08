package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class AnimPlayPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val index: AnimIndex,
    val layerId: ResourceLocation,
    val layerData: AnimLayerData
): CustomPacketPayload {
    constructor(syncer: Syncer, index: AnimIndex, layerId: ResourceLocation, layerData: AnimLayerData): this(syncer.syncerType, syncer.syncData, index, layerId, layerData)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleBothSide(payload: AnimPlayPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = payload.syncerType.getSyncer(level, payload.syncData)
            if (entity !is IAnimatable<*>) return
            val anim = AnimInstance.create(entity, payload.index)
            val controller = entity.animController
            val layer = entity.animController.getLayer(payload.layerId)
            layer.setAnimation(anim, payload.layerData)
            // 从单人客户端发来的同步需要再同步给其它玩家客户端
            if (!level.isClientSide) controller.playAnimToClient(payload.index, payload.layerId, payload.layerData, context.player() as? ServerPlayer)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<AnimPlayPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "anim_play"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, AnimPlayPayload::syncerType,
            SyncData.STREAM_CODEC, AnimPlayPayload::syncData,
            AnimIndex.STREAM_CODEC, AnimPlayPayload::index,
            ResourceLocation.STREAM_CODEC, AnimPlayPayload::layerId,
            AnimLayerData.STREAM_CODEC, AnimPlayPayload::layerData,
            ::AnimPlayPayload
        )
    }

}