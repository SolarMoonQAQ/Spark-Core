package cn.solarmoon.spark_core.pack.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoader
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SparkPackageReloadPayload(
    val packs: List<SparkPackage>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: SparkPackageReloadPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkPackLoader.reset()
                SparkPackLoader.readPackageGraph(true)
                SparkPackLoader.acceptRemote(payload.packs)
                context.player().sendSystemMessage(Component.translatable("command.${SparkCore.MOD_ID}.package.reload.success.client", payload.packs.size))
                SparkPackLoader.LOGGER.info("已从服务器重载 ${payload.packs.size} 个拓展包: ${payload.packs.map { it.meta.id }}")
                SparkPackLoader.readPackageContent(true, true)
                SparkPackLoader.injectPackageContent(true, true)
            }.exceptionally {
                context.disconnect(Component.literal("未能成功接受拓展包数据"))
                return@exceptionally null
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<SparkPackageReloadPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "spark_package_graph_reload"))

        @JvmStatic
        val STREAM_CODEC = StreamCodec.composite(
            SparkPackage.LIST_STREAM_CODEC, SparkPackageReloadPayload::packs,
            ::SparkPackageReloadPayload
        )
    }

}