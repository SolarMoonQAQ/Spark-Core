package cn.solarmoon.spark_core.resource2.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource2.SparkPackLoader
import cn.solarmoon.spark_core.resource2.graph.SparkPackGraph
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SparkPackageReloadPayload(
    val graph: SparkPackGraph,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SparkPackageReloadPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkPackLoader.reset()
                SparkPackLoader.graph.originNodes.putAll(payload.graph.originNodes)
                context.player().sendSystemMessage(Component.translatable("command.${SparkCore.MOD_ID}.package.reload.success.client", payload.graph.originNodes.size))
                SparkCore.LOGGER.info("已从服务器接收 {} 个拓展包", payload.graph.originNodes.size)
                SparkPackLoader.readPackageContent(true)
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受拓展包数据"))
                return@exceptionally null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SparkPackageReloadPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "spark_package_graph_reload"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SparkPackGraph.STREAM_CODEC, SparkPackageReloadPayload::graph,
            ::SparkPackageReloadPayload
        )
    }

}