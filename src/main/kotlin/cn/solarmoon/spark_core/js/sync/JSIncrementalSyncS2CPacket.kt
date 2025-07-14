package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.resource.payload.resource_sync.ChangeType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

data class JSIncrementalSyncS2CPacket(
    val modId: String,
    val apiId: String,           // JS API模块ID (如 "skill", "ik")
    val fileName: String,        // 脚本文件名 (如 "example.js")
    val operationType: ChangeType,
    val scriptContent: String    // 脚本内容，REMOVE操作时为空字符串
) : CustomPacketPayload {

    companion object {
        @JvmField
        val ID = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_incremental_sync")

        @JvmField
        val TYPE = CustomPacketPayload.Type<JSIncrementalSyncS2CPacket>(ID)

        @JvmField
        val STREAM_CODEC = object : StreamCodec<FriendlyByteBuf, JSIncrementalSyncS2CPacket> {
            override fun encode(buf: FriendlyByteBuf, packet: JSIncrementalSyncS2CPacket) {
                buf.writeUtf(packet.modId)
                buf.writeUtf(packet.apiId)
                buf.writeUtf(packet.fileName)
                buf.writeEnum(packet.operationType)
                buf.writeUtf(packet.scriptContent)
            }

            override fun decode(buf: FriendlyByteBuf): JSIncrementalSyncS2CPacket {
                val modId = buf.readUtf()
                val apiId = buf.readUtf()
                val fileName = buf.readUtf()
                val operationType = buf.readEnum(ChangeType::class.java)
                val scriptContent = buf.readUtf()
                return JSIncrementalSyncS2CPacket(modId, apiId, fileName, operationType, scriptContent)
            }
        }

        /**
         * 创建脚本添加/更新同步包
         */
        fun createForScriptAddOrUpdate(modId: String, apiId: String, fileName: String, scriptContent: String, isUpdate: Boolean = false): JSIncrementalSyncS2CPacket {
            return JSIncrementalSyncS2CPacket(
                modId,
                apiId,
                fileName,
                if (isUpdate) ChangeType.MODIFIED else ChangeType.ADDED,
                scriptContent
            )
        }

        /**
         * 创建脚本删除同步包
         */
        fun createForScriptRemove(modId: String, apiId: String, fileName: String): JSIncrementalSyncS2CPacket {
            return JSIncrementalSyncS2CPacket(
                modId,
                apiId,
                fileName,
                ChangeType.REMOVED,
                ""
            )
        }

        /**
         * 同步脚本删除到所有客户端
         */
        fun syncScriptRemovalToClients(modId: String, apiId: String, fileName: String) {
            val packet = createForScriptRemove(modId, apiId, fileName)
            PacketDistributor.sendToAllPlayers(packet)
            SparkCore.LOGGER.info("Sent JS script REMOVAL sync packet for $apiId/$fileName to all clients")
        }

        /**
         * 同步脚本添加/更新到所有客户端
         */
        fun syncScriptToClients(modId: String, apiId: String, fileName: String, scriptContent: String, isUpdate: Boolean = false) {
            val packet = createForScriptAddOrUpdate(modId, apiId, fileName, scriptContent, isUpdate)
            PacketDistributor.sendToAllPlayers(packet)
            val operation = if (isUpdate) "MODIFIED" else "ADDED"
            SparkCore.LOGGER.info("Sent JS script $operation sync packet for $apiId/$fileName to all clients")
        }

        /**
         * 客户端处理增量同步
         */
        fun handleInClient(packet: JSIncrementalSyncS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                try {
                    handleJSIncrementalSync(packet, context)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Error handling JS incremental sync packet", e)
                }
            }.exceptionally {
                SparkCore.LOGGER.error("处理 JSIncrementalSyncS2CPacket 时发生异常", it)
                null
            }
        }

        private fun handleJSIncrementalSync(packet: JSIncrementalSyncS2CPacket, context: IPayloadContext) {
            // 验证API是否存在
            if (!JSApi.Companion.ALL.containsKey(packet.apiId)) {
                SparkCore.LOGGER.warn("Received JS sync for unknown API: ${packet.apiId}")
                return
            }

            val api = JSApi.Companion.ALL[packet.apiId]!!
            val modId = packet.modId
            val player = context.player()
            val js = player.level().jsEngine
            val registry = cn.solarmoon.spark_core.registry.common.SparkRegistries.JS_SCRIPTS
            when (packet.operationType) {
                ChangeType.ADDED, ChangeType.MODIFIED -> {
                    // 创建 OJSScript 对象
                    val location = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "script/${packet.apiId}/${packet.fileName}")
                    val ojsScript = cn.solarmoon.spark_core.js.origin.OJSScript(
                        apiId = packet.apiId,
                        fileName = packet.fileName,
                        content = packet.scriptContent,
                        location = location
                    )

                    // 更新客户端动态注册表
                    try {
                        registry.registerDynamic(location, ojsScript, modId)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("更新客户端注册表失败: {}", e.message)
                    }

                    // 使用统一的脚本执行方法
                    js.reloadScript(ojsScript)

                    val operation = if (packet.operationType == ChangeType.MODIFIED) "更新" else "添加"
                    SparkCore.LOGGER.info("客户端${operation}JS脚本：模块：${packet.apiId} 文件：${packet.fileName}")
                }

                ChangeType.REMOVED -> {
                    // 从客户端动态注册表移除
                    try {
                        val location = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "script/${packet.apiId}/${packet.fileName}")
                        registry.unregisterDynamic(location)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.debug("从客户端注册表移除失败: {}", e.message)
                    }

                    // 使用统一的脚本卸载方法
                    js.unloadScript(packet.apiId, packet.fileName)

                    SparkCore.LOGGER.info("客户端删除JS脚本：模块：${packet.apiId} 文件：${packet.fileName}")
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<JSIncrementalSyncS2CPacket> {
        return TYPE
    }
}