package cn.solarmoon.spark_core.rpc.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.rpc.RpcHandler
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class RpcPayload(
    val methodName: String,
    val params: Map<String, String>
) : CustomPacketPayload {

    override fun type() = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<RpcPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "rpc")
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RpcPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RpcPayload::methodName,
            ByteBufCodecs.map(::HashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), RpcPayload::params,
            ::RpcPayload
        )

        @JvmStatic
        fun handleInServer(payload: RpcPayload, context: IPayloadContext) {
            // 调用 RPC 方法并处理返回结果
            val result = RpcHandler.callMethod(payload.methodName, payload.params)
            // 可以根据需要处理返回结果，例如发送响应给客户端
        }
    }
} 