package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.model.origin.OModel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Consumer

class ModelDataSendingTask(): ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        val modelData = ModelDataPayload(OModel.ORIGINS, OAnimationSet.ORIGINS)
        sender.accept(modelData)
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "model_sending_task"))
    }

    data class Return(val dummy: Int = 0): CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return TYPE
        }

        companion object {
            @JvmStatic
            fun onAct(payload: Return, context: IPayloadContext) {
                context.finishCurrentTask(ModelDataSendingTask.TYPE)
            }

            @JvmStatic
            val TYPE = CustomPacketPayload.Type<Return>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "model_sending_task_client_return"))
            @JvmStatic
            val STREAM_CODEC = StreamCodec.unit<FriendlyByteBuf, Return>(Return())
        }
    }

}