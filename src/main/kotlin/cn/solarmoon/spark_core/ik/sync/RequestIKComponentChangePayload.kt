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
 * Payload sent from Client -> Server to request adding or removing an IK component.
 */
class RequestIKComponentChangePayload(
    val targetEntityId: Int,
    val componentTypeId: ResourceLocation,
    val addComponent: Boolean // true to add, false to remove
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<RequestIKComponentChangePayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "request_ik_change"))

        @JvmStatic
        // Codec for serialization/deserialization
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestIKComponentChangePayload> = StreamCodec.composite(
            ByteBufCodecs.INT, RequestIKComponentChangePayload::targetEntityId,
            ResourceLocation.STREAM_CODEC, RequestIKComponentChangePayload::componentTypeId,
            ByteBufCodecs.BOOL, RequestIKComponentChangePayload::addComponent,
            ::RequestIKComponentChangePayload
        )

        @JvmStatic
        // Handler executed on the Server thread
        fun handleInServer(payload: RequestIKComponentChangePayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork // Ensure it's a server player
                val level = player.serverLevel() // Get server level safely

                // 调用 IKService 处理请求，而不是直接操作 IKManager
                IKService.handleComponentChangeRequest(
                    level,
                    player, // 传递请求者以供权限检查
                    payload.targetEntityId,
                    payload.componentTypeId,
                    payload.addComponent
                )

            }.exceptionally { // Add error handling
                SparkCore.LOGGER.error("Exception handling RequestIKComponentChangePayload", it)
                null
            }
        }
    }
}