package cn.solarmoon.spark_core.resource.sync

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 服务器向客户端广播的依赖变更包
 * @param resourceKey 资源标识 (namespace:path)
 * @param dependencies 最新依赖列表
 */
data class DepsChangedS2CPacket(
    val resourceKey: String,
    val dependencies: List<String>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DepsChangedS2CPacket> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<DepsChangedS2CPacket>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "s2c_deps_changed")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, DepsChangedS2CPacket> = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, DepsChangedS2CPacket::resourceKey,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list()), DepsChangedS2CPacket::dependencies,
            ::DepsChangedS2CPacket
        )

        @JvmStatic
        fun handleInClient(payload: DepsChangedS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
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
                    
                    MetadataManager.save(container)
                    */
                    SparkCore.LOGGER.warn("DepsChangedS2CPacket 的处理逻辑需要被重构以使用 ResourceGraphManager")

                    
                    // 仅在客户端通知GUI系统依赖已更改
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        try {
                            val currentScreen = Minecraft.getInstance().screen
                            if (currentScreen != null && currentScreen.javaClass.simpleName == "ResourceManagerScreen") {
                                // 使用反射调用方法，避免直接导入客户端类
                                val showStatusMethod = currentScreen.javaClass.getMethod("showStatusMessage", String::class.java)
                                showStatusMethod.invoke(currentScreen, "Dependencies updated for ${payload.resourceKey}")
                                
                                val refreshMethod = currentScreen.javaClass.getMethod("refreshAllPublic")
                                refreshMethod.invoke(currentScreen)
                            }
                        } catch (e: Exception) {
                            SparkCore.LOGGER.debug("Failed to notify GUI of dependency changes", e)
                        }
                    }
                    
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理依赖变更包失败: {}", e.message, e)
                }
            }
        }
    }
} 