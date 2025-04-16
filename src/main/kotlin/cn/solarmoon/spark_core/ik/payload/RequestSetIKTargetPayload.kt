package cn.solarmoon.spark_core.ik.payload

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.ik.service.IKService
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional

/**
 * Payload sent from Client -> Server to request setting or clearing an IK target.
 */
class RequestSetIKTargetPayload(
    val targetEntityId: Int,
    val chainName: String, // 使用链名称标识目标
    val targetPosition: Vec3? // 可空 Vec3: null 表示清除目标
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {

        @JvmStatic
        val TYPE = CustomPacketPayload.Type<RequestSetIKTargetPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "request_set_ik_target"))

        @JvmStatic
        // 编解码器
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestSetIKTargetPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, { it.targetEntityId },
            ByteBufCodecs.STRING_UTF8, { it.chainName },
            ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC), { Optional.ofNullable(it.targetPosition) },
            { targetEntityId, chainName, targetPosition -> RequestSetIKTargetPayload(targetEntityId, chainName, targetPosition.orElse(null)) }
        )

        @JvmStatic
        // 在服务器线程执行的处理器
        fun handleInServer(payload: RequestSetIKTargetPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.serverLevel()

                // 调用 IKService 处理请求
                IKService.handleSetIKTargetRequest(
                    level,
                    player,
                    payload.targetEntityId,
                    payload.chainName,
                    payload.targetPosition // 传递可空的 Vec3
                )

            }.exceptionally {
                SparkCore.LOGGER.error("Exception handling RequestSetIKTargetPayload", it)
                null
            }
        }
    }
}