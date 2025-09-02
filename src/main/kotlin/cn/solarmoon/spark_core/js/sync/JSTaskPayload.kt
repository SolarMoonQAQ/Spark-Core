package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class JSTaskPayload(
    val api: Map<String, Map<String, String>>
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: JSTaskPayload, context: IPayloadContext) {
            context.enqueueWork {
                JSApi.clientApiCache = payload.api
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受脚本数据：${payload.api.keys}"))
                return@exceptionally null
            }.thenAccept {
                context.reply(JSSendingTask.Return())
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<JSTaskPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_task_data"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.map({mutableMapOf()}, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)), JSTaskPayload::api,
            ::JSTaskPayload
        )
    }

}