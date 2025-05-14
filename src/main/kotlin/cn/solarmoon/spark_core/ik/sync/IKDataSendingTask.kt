package cn.solarmoon.spark_core.ik.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Consumer

/**
 * 用于从服务器向客户端发送IK约束数据的配置任务。
 * 类似于ModelDataSendingTask，但针对IK约束。
 */
class IKDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        // 创建包含所有IK约束的负载
        val ikData = IKDataPayload(OIKConstraint.ORIGINS)
        sender.accept(ikData)
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_data_sending_task")
        )
    }

    /**
     * 返回客户端发送到服务器以确认接收到IK数据的负载。
     */
    data class Return(val dummy: Int = 0) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return TYPE
        }

        companion object {
            @JvmStatic
            fun onAct(payload: Return, context: IPayloadContext) {
                // 将任务标记为完成
                context.finishCurrentTask(IKDataSendingTask.TYPE)
            }

            @JvmStatic
            val TYPE = CustomPacketPayload.Type<Return>(
                ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_data_sending_task_client_return")
            )
            
            @JvmStatic
            val STREAM_CODEC = StreamCodec.unit<FriendlyByteBuf, Return>(Return())
        }
    }
}