package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.origin.OJSScript
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.collections.set

class JSPayload(
    val api: Map<String, Map<String, String>>
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: JSPayload, context: IPayloadContext) {
            JSApi.clientApiCache = payload.api
            val js = context.player().level().jsEngine
            payload.api.forEach { apiId, v ->
                js.validateApi(apiId)
                val api = JSApi.ALL[apiId]!!
                v.forEach {
                    api.onReload()
                    val (fileName, value) = it
                    val scriptLocation = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_script/${apiId}/${fileName}")
                    val script = OJSScript(
                        apiId = apiId,
                        fileName = fileName,
                        content = value,
                        location = scriptLocation
                    )
                    js.executeScript(script)
                    api.valueCache[fileName] = value
                    context.player().sendSystemMessage(Component.literal("已从服务器接收脚本数据：模块：$apiId 文件：$fileName"))
                }
                api.onLoad()
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<JSPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_data"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(::LinkedHashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.map({mutableMapOf()}, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)), JSPayload::api,
            ::JSPayload
        )
    }

}