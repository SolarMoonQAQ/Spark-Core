package cn.solarmoon.spark_core.ik.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.LinkedHashMap

data class IKDataPayload(
    val constraints: LinkedHashMap<ResourceLocation, OIKConstraint>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return TYPE
    }

    companion object {
        @JvmStatic
        fun handleInClient(payload: IKDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                // 清除现有约束并替换为服务器中的约束
                OIKConstraint.ORIGINS.clear()
                payload.constraints.forEach { (id, constraint) ->
                    OIKConstraint.ORIGINS[id] = constraint
                }
                SparkCore.LOGGER.info("从服务器接收了 ${payload.constraints.size} 个IK约束")
            }.exceptionally {
                context.disconnect(Component.literal("接收IK约束数据失败"))
                return@exceptionally null
            }.thenAccept {
                // 向服务器发送确认
                context.reply(IKDataSendingTask.Return())
            }
        }

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<IKDataPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_constraint_data")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, IKDataPayload> = StreamCodec.composite(
            OIKConstraint.ORIGIN_MAP_STREAM_CODEC, IKDataPayload::constraints,
            ::IKDataPayload
        )
    }
}