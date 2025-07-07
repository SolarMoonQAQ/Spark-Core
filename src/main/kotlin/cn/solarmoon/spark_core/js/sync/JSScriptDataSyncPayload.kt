package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.LinkedHashMap
import net.minecraft.network.codec.ByteBufCodecs

/**
 * 用于全量同步JS脚本数据的Payload
 * @param scripts JS脚本数据 Map<ResourceLocation, OJSScript>
 */
data class JSScriptDataSyncPayload(
    val scripts: LinkedHashMap<ResourceLocation, OJSScript>
) : CustomPacketPayload {

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<JSScriptDataSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "js_script_data_full_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, JSScriptDataSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.map(
                ::LinkedHashMap,
                ResourceLocation.STREAM_CODEC,
                OJSScript.STREAM_CODEC
            ),
            JSScriptDataSyncPayload::scripts,
            ::JSScriptDataSyncPayload
        )

        /**
         * 在客户端处理此Payload的逻辑
         */
        @JvmStatic
        fun handleInClient(payload: JSScriptDataSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("客户端接收到全量JS脚本同步数据. 脚本数量: ${payload.scripts.size}")
                
                try {
                    // 清空客户端动态注册表
                    SparkRegistries.JS_SCRIPTS.clearDynamic()
                    
                    // 将全量同步数据注册到客户端动态注册表
                    payload.scripts.forEach { (location, jsScript) ->
                        val resourceKey = net.minecraft.resources.ResourceKey.create(SparkRegistries.JS_SCRIPTS.key(), location)
                        SparkRegistries.JS_SCRIPTS.register(resourceKey, jsScript, net.minecraft.core.RegistrationInfo.BUILT_IN)
                        SparkCore.LOGGER.debug("客户端注册JS脚本: {} -> API: {}, 文件: {}", location, jsScript.apiId, jsScript.fileName)
                    }
                    
                    // 更新传统的clientApiCache以保持兼容性
                    updateClientApiCache(payload.scripts.values)
                    
                    SparkCore.LOGGER.info("客户端动态注册表已更新，包含 ${payload.scripts.size} 个JS脚本")

                    // 刷新JS脚本浏览器界面（如果当前正在打开）
                    try {
                        val refreshMethod = Class.forName("cn.solarmoon.spark_core.client.gui.screen.JSScriptBrowserScreen")
                            .getDeclaredMethod("refreshCurrentInstance")
                        refreshMethod.invoke(null)
                    } catch (e: Exception) {
                        // 界面可能没有打开，忽略错误
                        SparkCore.LOGGER.debug("刷新JS脚本浏览器界面失败（可能界面未打开）: ${e.message}")
                    }
                    
                    // 发送确认给服务器
                    val ack = JSScriptDataSendingTask.AckPayload(payload.scripts.size)
                    context.reply(ack) 
                    SparkCore.LOGGER.info("已向服务器发送JS脚本全量同步确认")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理JS脚本全量同步数据时发生错误", e)
                }
            }.exceptionally { e -> 
                SparkCore.LOGGER.error("客户端处理JS脚本全量同步时发生异常", e)
                null
            }
        }

        /**
         * 更新传统的clientApiCache以保持兼容性
         */
        private fun updateClientApiCache(scripts: Collection<OJSScript>) {
            val apiCacheMap = scripts.groupBy { it.apiId }
                .mapValues { (_, scriptList) ->
                    scriptList.associate { it.fileName to it.content }
                }
            
            JSApi.clientApiCache = apiCacheMap
            SparkCore.LOGGER.debug("已更新clientApiCache，包含 {} 个API模块", apiCacheMap.size)
        }
    }

    override fun type(): CustomPacketPayload.Type<*> {
        return TYPE
    }
}