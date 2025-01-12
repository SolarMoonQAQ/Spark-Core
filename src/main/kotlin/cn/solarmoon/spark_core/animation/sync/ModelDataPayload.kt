package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class ModelDataPayload(
    val models: LinkedHashMap<ResourceLocation, OModel>,
    val animationSets: LinkedHashMap<ResourceLocation, OAnimationSet>
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: ModelDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                OModel.ORIGINS.clear()
                OAnimationSet.ORIGINS.clear()
                payload.models.forEach { id, model ->
                    OModel.ORIGINS[id] = model
                }
                payload.animationSets.forEach { id, anim ->
                    OAnimationSet.ORIGINS[id] = anim
                }
                SparkCore.LOGGER.info("已从服务器接收所有模型动画数据")
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受模型和动画数据"))
                return@exceptionally null
            }.thenAccept {
                context.reply(ModelDataSendingTask.Return())
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<ModelDataPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "whole_model_data"))

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ModelDataPayload> = StreamCodec.composite(
            OModel.ORIGIN_MAP_STREAM_CODEC, ModelDataPayload::models,
            OAnimationSet.ORIGIN_MAP_STREAM_CODEC, ModelDataPayload::animationSets,
            ::ModelDataPayload
        )
    }

}