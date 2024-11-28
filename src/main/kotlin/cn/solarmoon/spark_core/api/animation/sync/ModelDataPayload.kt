package cn.solarmoon.spark_core.api.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.api.animation.anim.AnimationSet
import cn.solarmoon.spark_core.api.animation.model.CommonModel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class ModelDataPayload(
    val models: MutableMap<ResourceLocation, CommonModel>,
    val animationSets: MutableMap<ResourceLocation, AnimationSet>,
    val playerAnimationSet: AnimationSet
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: ModelDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                CommonModel.ORIGINS.clear()
                AnimationSet.ORIGINS.clear()
                AnimationSet.PLAYER_ORIGINS.animations.clear()
                payload.models.forEach { id, model ->
                    CommonModel.ORIGINS[id] = model
                }
                payload.animationSets.forEach { id, anim ->
                    AnimationSet.ORIGINS[id] = anim
                }
                AnimationSet.PLAYER_ORIGINS.animations.addAll(payload.playerAnimationSet.animations)
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
            CommonModel.ORIGIN_MAP_STREAM_CODEC, ModelDataPayload::models,
            AnimationSet.ORIGIN_MAP_STREAM_CODEC, ModelDataPayload::animationSets,
            AnimationSet.STREAM_CODEC, ModelDataPayload::playerAnimationSet,
            ::ModelDataPayload
        )
    }

}