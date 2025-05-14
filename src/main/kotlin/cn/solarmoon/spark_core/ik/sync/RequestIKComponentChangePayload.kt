package cn.solarmoon.spark_core.ik.sync

// import cn.solarmoon.spark_core.util.logger // 移除旧的 logger 导入
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.service.IKService
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 客户端 -> 服务器发送的请求添加或移除IK组件的负载。
 */
class RequestIKComponentChangePayload(
    val targetEntityId: Int,
    val componentTypeId: ResourceLocation,
    val addComponent: Boolean // true 表示添加，false 表示移除
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<RequestIKComponentChangePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "request_ik_change"))

        @JvmStatic
        // 序列化/反序列化的编解码器
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestIKComponentChangePayload> = StreamCodec.composite(
            ByteBufCodecs.INT, RequestIKComponentChangePayload::targetEntityId,
            ResourceLocation.STREAM_CODEC, RequestIKComponentChangePayload::componentTypeId,
            ByteBufCodecs.BOOL, RequestIKComponentChangePayload::addComponent,
            ::RequestIKComponentChangePayload
        )

        @JvmStatic
        // 在服务器线程执行的处理器
        fun handleInServer(payload: RequestIKComponentChangePayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork // 确保是服务器玩家
                val level = player.serverLevel() // 安全获取服务器级别

                // 调用 IKService 处理请求，而不是直接操作 IKManager
                IKService.handleComponentChangeRequest(
                    level,
                    player, // 传递请求者以供权限检查
                    payload.targetEntityId,
                    payload.componentTypeId,
                    payload.addComponent
                )

            }.exceptionally { // 添加错误处理
                SparkCore.LOGGER.error("处理 RequestIKComponentChangePayload 时发生异常", it)
                null
            }
        }
    }
}