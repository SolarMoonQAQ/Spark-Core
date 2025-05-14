package cn.solarmoon.spark_core.ik.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.component.IKComponentType
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf // Ensure correct import if needed
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 用于在服务器和客户端之间同步IK组件激活状态的有效载荷。
 * 通常由服务器发送到客户端。
 */
class IKComponentSyncPayload private constructor(
    val syncerType: SyncerType,       // Identifies the host entity type
    val syncData: SyncData,         // Identifies the specific host entity instance
    val componentTypeId: ResourceLocation, // The ID of the IKComponentType being added/removed
    val active: Boolean             // True if the component should be active, false if inactive (removed)
) : CustomPacketPayload {
    // 便捷构造函数
    constructor(host: IEntityAnimatable<*>, type: IKComponentType, active: Boolean) : this(
        host.syncerType, // Assuming IEntityAnimatable provides these (inherited from IAnimatable/Syncer?)
        host.syncData,   // Assuming IEntityAnimatable provides these
        type.id,
        active
    )

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmStatic
        val TYPE = CustomPacketPayload.Type<IKComponentSyncPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "ik_component_sync"))

        @JvmStatic
        // 如果SyncData或SyncerType编解码器需要，使用RegistryFriendlyByteBuf
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, IKComponentSyncPayload> = StreamCodec.composite(
            SyncerType.STREAM_CODEC, IKComponentSyncPayload::syncerType,
            SyncData.STREAM_CODEC, IKComponentSyncPayload::syncData,
            ResourceLocation.STREAM_CODEC, IKComponentSyncPayload::componentTypeId,
            ByteBufCodecs.BOOL, IKComponentSyncPayload::active,
            ::IKComponentSyncPayload
        )

        @JvmStatic
        // 处理器在客户端线程中执行
        fun handleInClient(payload: IKComponentSyncPayload, context: IPayloadContext) {
            // 确保在主客户端线程中执行
            context.enqueueWork {
                val level = context.player().level() ?: return@enqueueWork // 使用安全调用获取玩家所在维度
                // 查找主机实体
                val host = payload.syncerType.getSyncer(level, payload.syncData) as? IEntityAnimatable<*> ?: return@enqueueWork

                // 从注册表中查找组件类型
                val componentType = SparkRegistries.IK_COMPONENT_TYPE.get(payload.componentTypeId) ?: run { // 通过.get()访问注册表
                    return@enqueueWork
                }

                // 在客户端管理器中添加或移除组件
                if (payload.active) {
                    host.ikManager.addComponent(componentType) // 管理器处理重复项和链构建
                } else {
                    host.ikManager.removeComponent(componentType.chainName) // 使用chainName移除
                }
            }.exceptionally { // 为入队任务添加错误处理
                null
            }
        }
    }
}