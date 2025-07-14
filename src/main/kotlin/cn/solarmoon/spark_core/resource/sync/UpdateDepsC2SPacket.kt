package cn.solarmoon.spark_core.resource.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 客户端向服务器发送的依赖更新包
 * @param resourceKey 资源标识 (namespace:path)
 * @param dependencies 依赖列表 (namespace:path)
 */
data class UpdateDepsC2SPacket(
    val resourceKey: String,
    val dependencies: List<String>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<UpdateDepsC2SPacket> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<UpdateDepsC2SPacket>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "c2s_update_deps")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, UpdateDepsC2SPacket> = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, UpdateDepsC2SPacket::resourceKey,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list()), UpdateDepsC2SPacket::dependencies,
            ::UpdateDepsC2SPacket
        )

        @JvmStatic
        fun handleInServer(payload: UpdateDepsC2SPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
                    val resourceLocation = ResourceLocation.parse(payload.resourceKey)
                    val node = ResourceGraphManager.getResourceNode(resourceLocation)
                    if (node == null) {
                        SparkCore.LOGGER.warn("收到未知资源的依赖更新包: ${payload.resourceKey}")
                        return@enqueueWork
                    }

                    // TODO: 需要在ResourceGraphManager中实现更新依赖的逻辑
                    // 暂时注释掉旧逻辑
                    /*
                    val container = MetadataContainer(payload.resourceKey)
                    val resourceDeps = payload.dependencies.map { depString ->
                        // 解析依赖字符串，格式：resourceId@version:type
                        val parts = depString.split(":")
                        val idAndVersion = parts[0]
                        val type = if (parts.size > 1) ODependencyType.valueOf(parts[1]) else ODependencyType.HARD
                        
                        val versionIndex = idAndVersion.indexOf("@")
                        val id = if (versionIndex > 0) idAndVersion.substring(0, versionIndex) else idAndVersion
                        val version = if (versionIndex > 0) idAndVersion.substring(versionIndex + 1) else null
                        
                        OResourceDependency(
                            id = ResourceLocation.parse(id),
                            type = type,
                            path = id,
                            version = version
                        )
                    }
                    container.setDependencies(payload.resourceKey, resourceDeps)
                    
                    if (MetadataManager.save(container)) {
                        PacketDistributor.sendToAllPlayers(
                            DepsChangedS2CPacket(payload.resourceKey, payload.dependencies)
                        )
                    }
                    */
                    SparkCore.LOGGER.warn("UpdateDepsC2SPacket 的处理逻辑需要被重构以使用 ResourceGraphManager")

                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理依赖更新包失败: {}", e.message, e)
                }
            }
        }
    }
} 