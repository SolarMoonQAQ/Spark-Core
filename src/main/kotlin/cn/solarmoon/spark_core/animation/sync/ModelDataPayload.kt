package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class ModelDataPayload(
    val models: LinkedHashMap<ResourceLocation, OModel>,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: ModelDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                // 清理旧数据
                OModel.ORIGINS.clear()
                // 获取动态注册表
                val dynamicRegistry = SparkRegistries.MODELS
                // 更新静态缓存并注册到动态注册表
                payload.models.forEach { id, model ->
                    OModel.ORIGINS[id] = model
                    // 客户端避免触发回调循环
                    val moduleId = id.namespace // 从ResourceLocation提取模块ID
                    dynamicRegistry.registerDynamic(id, model, moduleId, replace = true, triggerCallback = false)
                    SparkCore.LOGGER.debug("全量同步：已注册模型到动态注册表: {}", id)
                }

                SparkCore.LOGGER.info("已从服务器接收所有 {} 个模型并注册到动态注册表", payload.models.size)
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
            ::ModelDataPayload
        )
    }
}