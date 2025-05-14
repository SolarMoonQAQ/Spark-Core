package cn.solarmoon.spark_core.ik.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.animation.IEntityAnimatable

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional

/**
 * 服务器 -> 客户端发送的同步链的权威IK目标位置的负载。
 */
class IKSyncTargetPayload(
    val targetEntityId: Int,
    val chainName: String,
    val targetPosition: Vec3? // 可空 Vec3: null 表示清除目标
) : CustomPacketPayload {

    // 方便构造函数（可选，但可能有用）
    constructor(host: IEntityAnimatable<*>, chainName: String, targetPosition: Vec3?) : this(
        (host as? net.minecraft.world.entity.Entity)?.id ?: -1, // 安全获取实体ID
        chainName,
        targetPosition
    )

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<IKSyncTargetPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "sync_ik_target"))

        @JvmStatic
        // 编解码器
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, IKSyncTargetPayload> = StreamCodec.composite(
            ByteBufCodecs.INT, { it.targetEntityId },
            ByteBufCodecs.STRING_UTF8, { it.chainName },
            ByteBufCodecs.optional(SerializeHelper.VEC3_STREAM_CODEC), { Optional.ofNullable(it.targetPosition) },
            { targetEntityId, chainName, targetPosition -> IKSyncTargetPayload(targetEntityId, chainName, targetPosition.orElse(null)) }
        )

        @JvmStatic
        // 在客户端线程执行的处理器
        fun handleInBothSide(payload: IKSyncTargetPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork
                val targetEntity = level.getEntity(payload.targetEntityId)

                if (targetEntity == null) {
                    // 实体可能在客户端上尚未存在或已被移除
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: 未在客户端找到目标实体 ${sync.targetEntityId}")
                    return@enqueueWork
                }

                val host = targetEntity as? IEntityAnimatable<*> ?: return@enqueueWork // 检查是否为 IEntityAnimatable

                // 根据接收到的权威状态更新客户端的目标映射
                if (payload.targetPosition != null) {
                    host.ikTargetPositions[payload.chainName] = payload.targetPosition
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: 为实体 ${sync.targetEntityId} 的链 '${sync.chainName}' 设置客户端目标")
                } else {
                    host.ikTargetPositions.remove(payload.chainName)
                    // SparkCore.LOGGER.debug("IKSyncTargetPayload: 清除实体 ${sync.targetEntityId} 的链 '${sync.chainName}' 的客户端目标")
                }
            }.exceptionally {
                SparkCore.LOGGER.error("处理 IKSyncTargetPayload 时发生异常", it)
                null
            }
        }
    }
}