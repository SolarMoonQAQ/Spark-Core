package cn.solarmoon.spark_core.js.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.network.ConfigurationTask
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Consumer
import net.minecraft.network.codec.ByteBufCodecs
import java.util.LinkedHashMap

/**
 * 配置任务，用于在新玩家加入时向其发送全量的JS脚本数据。
 */
class JSScriptDataSendingTask : ICustomConfigurationTask {

    override fun run(sender: Consumer<CustomPacketPayload?>) {
        SparkCore.LOGGER.info("服务器端 JSScriptDataSendingTask: 准备向新玩家发送全量 JS脚本数据...")
        
        // 从 SparkRegistries.JS_SCRIPTS 注册表获取所有数据（包括静态注册的和动态注册的）
        val scriptsToSync = LinkedHashMap<ResourceLocation, OJSScript>()
        
        SparkRegistries.JS_SCRIPTS?.let { registry ->
            // 获取所有注册的条目（包括静态和动态）
            registry.entrySet().forEach { entry ->
                val registryKey = entry.key
                val jsScript = entry.value
                scriptsToSync[registryKey.location()] = jsScript
                SparkCore.LOGGER.debug("从注册表获取 JS脚本: ${registryKey.location()} -> API: ${jsScript.apiId}, 文件: ${jsScript.fileName}")
            }
            SparkCore.LOGGER.info("从注册表获取到 ${scriptsToSync.size} 个 JS脚本（包括静态和动态注册的）")
        }

        if (scriptsToSync.isNotEmpty()) {
            val payload = JSScriptDataSyncPayload(scriptsToSync)
            try { sender.accept(payload) } catch (t: Throwable) { System.err.println("[JSScriptDataSendingTask] send failed: " + t.toString()); t.printStackTrace(); return }
            SparkCore.LOGGER.info("成功向新玩家发送了包含 ${scriptsToSync.size} 个 JS脚本的同步包（来源：完整注册表）。等待客户端确认...")
        } else {
            SparkCore.LOGGER.info("注册表中没有加载任何 JS脚本数据，将向新玩家发送空的同步包。")
            val payload = JSScriptDataSyncPayload(LinkedHashMap())
            try { sender.accept(payload) } catch (t: Throwable) { System.err.println("[JSScriptDataSendingTask] send failed: " + t.toString()); t.printStackTrace(); return }
        }
    }

    override fun type(): ConfigurationTask.Type {
        return TYPE
    }

    companion object {
        @JvmStatic
        val TYPE = ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/js_script_data_sending_task"))
    }

    /**
     * 客户端在接收并处理完 JSScriptDataSyncPayload 后，发送此 Payload 给服务器以确认。
     */
    data class AckPayload(val receivedCount: Int) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmStatic
            val TYPE = CustomPacketPayload.Type<AckPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "config/js_script_data_ack"))

            @JvmStatic
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AckPayload> = StreamCodec.composite(
                ByteBufCodecs.INT, AckPayload::receivedCount,
                ::AckPayload
            )

            @JvmStatic
            fun handleOnServer(payload: AckPayload, context: IPayloadContext) {
                SparkCore.LOGGER.info("服务器收到 JSScriptDataSendingTask.AckPayload，客户端已接收 ${payload.receivedCount} 个JS脚本。完成配置任务。")
                context.finishCurrentTask(JSScriptDataSendingTask.TYPE)
            }
        }
    }
} 

