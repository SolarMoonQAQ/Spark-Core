package cn.solarmoon.spark_core.pack.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoader
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Consumer

class SparkPackageSendingTask(): ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload>) {
        sender.accept(SparkPackagePayload(SparkPackLoader.collectRemote()))
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "spark_package_graph_task"))
    }

    data class Return(val dummy: Int = 0): CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return TYPE
        }

        companion object {
            @JvmStatic
            fun onAct(payload: Return, context: IPayloadContext) {
                context.finishCurrentTask(SparkPackageSendingTask.TYPE)
            }

            @JvmStatic
            val TYPE = CustomPacketPayload.Type<Return>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "spark_package_graph_task_client_return"))
            @JvmStatic
            val STREAM_CODEC = StreamCodec.unit<FriendlyByteBuf, Return>(Return())
        }
    }
}