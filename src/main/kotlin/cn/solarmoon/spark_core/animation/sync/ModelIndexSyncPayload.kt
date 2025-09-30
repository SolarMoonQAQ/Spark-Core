package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 同步 ModelIndex 从服务端到客户端的负载, 用于切换模型
 */
data class ModelIndexSyncPayload(
    val syncerType: SyncerType<*>,
    val syncData: SyncData,
    val modelIndex: ModelIndex?
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<ModelIndexSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_model_index")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelIndexSyncPayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, { it.syncerType },
            SyncData.STREAM_CODEC, { it.syncData },
            ModelIndex.STREAM_CODEC, { it.modelIndex },
            ::ModelIndexSyncPayload
        )

        @JvmStatic
        fun handleInClient(payload: ModelIndexSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? IEntityAnimatable<*> ?: return@enqueueWork
                host.modelController.setModel(payload.modelIndex)
                SparkCore.LOGGER.info("接收到实体同步 ModelIndex 完成")
            }.exceptionally {
                context.disconnect(Component.literal("接收 ModelIndex 数据失败"))
                null
            }
        }
    }
}
