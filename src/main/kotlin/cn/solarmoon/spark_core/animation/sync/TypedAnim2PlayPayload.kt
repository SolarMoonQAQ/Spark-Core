package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation2
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class TypedAnim2PlayPayload private constructor(
    val syncerType: SyncerType,
    val syncData: SyncData,
    val animId: Int,
    val layerId: ResourceLocation,
    val layerData: AnimLayerData
): CustomPacketPayload {
    constructor(syncer: Syncer, animId: Int, layerId: ResourceLocation, layerData: AnimLayerData): this(syncer.syncerType, syncer.syncData, animId, layerId, layerData)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleBothSide(payload: TypedAnim2PlayPayload, context: IPayloadContext) {
            val level = context.player().level()
            val entity = payload.syncerType.getSyncer(level, payload.syncData)
            if (entity !is IAnimatable<*>) return
            val anim = TypedAnimation2.get(payload.animId) ?: return
            anim.play(entity, payload.layerId, payload.layerData)
            // 从单人客户端发来的同步需要再同步给其它玩家客户端
            if (!level.isClientSide) anim.playToClient(entity, payload.layerId, payload.layerData, context.player() as? ServerPlayer)
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TypedAnim2PlayPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_anim_play2"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, TypedAnim2PlayPayload::syncerType,
            SyncData.STREAM_CODEC, TypedAnim2PlayPayload::syncData,
            ByteBufCodecs.INT, TypedAnim2PlayPayload::animId,
            ResourceLocation.STREAM_CODEC, TypedAnim2PlayPayload::layerId,
            AnimLayerData.STREAM_CODEC, TypedAnim2PlayPayload::layerData,
            ::TypedAnim2PlayPayload
        )
    }

}