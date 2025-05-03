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
 * Configuration task for sending IK constraint data from server to client.
 * Similar to ModelDataSendingTask but for IK constraints.
 */
class IKDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        // Create a payload with all IK constraints
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
     * Return payload sent from client to server to confirm receipt of IK data.
     */
    data class Return(val dummy: Int = 0) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return TYPE
        }

        companion object {
            @JvmStatic
            fun onAct(payload: Return, context: IPayloadContext) {
                // Mark the task as complete
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
