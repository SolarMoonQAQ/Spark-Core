package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class TypedAnimPlayPayload private constructor(
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
        fun handleBothSide(payload: TypedAnimPlayPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                if (level.isClientSide) {
                    if (!AnimationSyncState.isTypedAnimationsReady()) {
                        AnimationSyncState.enqueueTypedAnimPlay(payload)
                        SparkCore.LOGGER.warn("客户端动画索引未就绪，已暂存一次动画播放请求 id={}", payload.animId)
                        return@enqueueWork
                    } else {
                        AnimationSyncState.flushTypedAnimPlays(level)
                    }
                }
                val entity = payload.syncerType.getSyncer(level, payload.syncData)
                if (entity !is IAnimatable<*>) return@enqueueWork
                val anim = SparkRegistries.TYPED_ANIMATION.byId(payload.animId) ?: return@enqueueWork
                anim.play(entity, payload.layerId, payload.layerData)
                // 从单人客户端发来的同步需要再同步给其它玩家客户端
                if (!level.isClientSide) anim.playToClient(entity, payload.layerId, payload.layerData, context.player() as? ServerPlayer)
            }.exceptionally {
                SparkCore.LOGGER.error("处理 TypedAnimPlayPayload 时发生异常", it)
                null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<TypedAnimPlayPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_anim_play"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SyncerType.STREAM_CODEC, TypedAnimPlayPayload::syncerType,
            SyncData.STREAM_CODEC, TypedAnimPlayPayload::syncData,
            ByteBufCodecs.INT, TypedAnimPlayPayload::animId,
            ResourceLocation.STREAM_CODEC, TypedAnimPlayPayload::layerId,
            AnimLayerData.STREAM_CODEC, TypedAnimPlayPayload::layerData,
            ::TypedAnimPlayPayload
        )
    }

}
