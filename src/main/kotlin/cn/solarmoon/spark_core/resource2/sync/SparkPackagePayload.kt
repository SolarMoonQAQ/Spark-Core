package cn.solarmoon.spark_core.resource2.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource2.SparkPackLoader
import cn.solarmoon.spark_core.resource2.graph.SparkPackGraph
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SparkPackagePayload(
    val graph: SparkPackGraph,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SparkPackagePayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkPackLoader.reset()
                SparkPackLoader.graph.originNodes.putAll(payload.graph.originNodes)
                SparkPackLoader.LOGGER.info(buildString {
                    appendLine()
                    appendLine("💻已从服务器接收拓展包💻")
                    append("[")
                    append(SparkPackLoader.graph.originNodes.keys.joinToString(", "))
                    append("]\n")
                })
                SparkPackLoader.readPackageContent(true)
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受拓展包数据"))
                return@exceptionally null
            }.thenAccept {
                context.reply(SparkPackageSendingTask.Return())
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SparkPackagePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "spark_package_graph"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SparkPackGraph.STREAM_CODEC, SparkPackagePayload::graph,
            ::SparkPackagePayload
        )
    }

}