package cn.solarmoon.spark_core.pack.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoader
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SparkPackagePayload(
    val packs: List<SparkPackage>,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SparkPackagePayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkPackLoader.acceptRemote(payload.packs)
                SparkPackLoader.LOGGER.info(buildString {
                    appendLine()
                    appendLine("💻已从服务器接收拓展包💻")
                    append("[")
                    append(payload.packs.map { it.meta.id }.joinToString(", "))
                    appendLine("]")
                    append("包含模块: ${payload.packs.map { it.modules }.distinct().joinToString(", ")}")
                })
                SparkPackLoader.readPackageContent(true, true)
                SparkPackLoader.injectPackageContent(true, true)
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
            SparkPackage.LIST_STREAM_CODEC, SparkPackagePayload::packs,
            ::SparkPackagePayload
        )
    }

}