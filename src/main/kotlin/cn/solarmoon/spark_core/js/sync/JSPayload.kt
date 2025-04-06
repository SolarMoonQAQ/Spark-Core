package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.sync.ModelDataPayload
import cn.solarmoon.spark_core.animation.sync.ModelDataSendingTask
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.SparkJS
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.collections.set

class JSPayload(
    val api: Map<String, Map<String, String>>,
    val reload: Boolean
): CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: JSPayload, context: IPayloadContext) {
            context.enqueueWork {
                payload.api.forEach { apiId, v ->
                    SparkJS.validateApi(apiId)
                    val api = SparkJS.allApi[apiId]!!
                    v.forEach {
                        if (payload.reload) api.onReload()
                        val (fileName, value) = it
                        SparkJS.eval(value, "$apiId - $fileName")
                        api.valueCache[fileName] = value
                        if (payload.reload) context.player().sendSystemMessage(Component.literal("已从服务器接收脚本数据：模块：$apiId 文件：$fileName"))
                        SparkCore.LOGGER.info("已从服务器接收脚本数据：模块：$apiId 文件：$fileName")
                    }
                    api.onLoad()
                }
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受脚本数据：${payload.api.keys}"))
                return@exceptionally null
            }.thenAccept {
                context.reply(JSSendingTask.Return())
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<JSPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_data"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map({mutableMapOf()}, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.map({mutableMapOf()}, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)), JSPayload::api,
            ByteBufCodecs.BOOL, JSPayload::reload,
            ::JSPayload
        )
    }

}