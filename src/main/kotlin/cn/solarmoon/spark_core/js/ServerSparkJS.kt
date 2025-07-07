package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.origin.OJSScript
import cn.solarmoon.spark_core.js.sync.JSPayload
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.io.File
import java.nio.file.Files
import kotlin.io.extension
import kotlin.io.readText

class ServerSparkJS: SparkJS() {

    /**
     * 线程安全地执行脚本
     */
    override fun executeScript(script: OJSScript) {
        val server = ServerLifecycleHooks.getCurrentServer()
        if (server != null) {
            // 确保在服务器主线程执行
            server.execute {
                super.executeScript(script)
            }
        } else {
            // 如果服务器未启动，直接执行（启动阶段）
            super.executeScript(script)
        }
    }

    /**
     * 重写 reloadScript 以添加网络同步
     */
    override fun reloadScript(script: OJSScript) {
        super.reloadScript(script)
        
        // 同步到客户端
        val api = JSApi.ALL[script.apiId]
        if (api != null) {
            PacketDistributor.sendToAllPlayers(JSPayload(mapOf(script.apiId to mapOf(script.fileName to script.content))))
        }
    }

    /**
     * 重写 unloadScript 以添加网络同步
     */
    override fun unloadScript(apiId: String, fileName: String) {
        super.unloadScript(apiId, fileName)
        
        // 通知客户端脚本已被移除
        // 这里可以发送特殊的移除包，暂时使用空内容表示移除
        PacketDistributor.sendToAllPlayers(JSPayload(mapOf(apiId to mapOf(fileName to ""))))
    }

    /**
     * 动态加载对应api路径下的所有脚本
     * @deprecated 推荐使用 loadAllFromRegistry() 从注册表加载
     */
    @Deprecated("Use loadAllFromRegistry() instead")
    fun load(api: JSApi) {
        validateApi(api)
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val scriptDir = File(gameDir, "sparkcore/script/${api.id}")

        // 检查目录是否存在且是一个目录
        if (!scriptDir.exists() || !scriptDir.isDirectory) {
            SparkCore.LOGGER.warn("脚本目录不存在或不是目录: ${scriptDir.path}")
            return
        }

        // 遍历目录及其子目录，加载所有 .js 文件
        Files.walk(scriptDir.toPath())
            .filter { it.toFile().isFile && it.toFile().extension == "js" }
            .forEach { path ->
                val file = path.toFile()
                val value = file.readText()
                eval(value, file.path)
                // 移除对 valueCache 的手动操作，现在从注册表获取
                // api.valueCache[file.name] = value
                SparkCore.LOGGER.info("已加载脚本：模块：${api.id} 文件：${file.name}")
            }

        api.onLoad()
    }

    /**
     * @deprecated 推荐使用 loadAllFromRegistry() 从注册表加载
     */
    @Deprecated("Use loadAllFromRegistry() instead")
    fun loadAll() = JSApi.ALL.forEach { load(it.value) }

    /**
     * 动态重载对应api路径下的所有脚本并发送重载结果到客户端，客户端会进行一次[JSApi.onReload]
     * @deprecated 推荐使用 reloadScript() 进行单脚本重载
     */
    @Deprecated("Use reloadScript() for individual script reloading")
    fun reload(api: JSApi) {
        validateApi(api)
        api.onReload()
        load(api)
        // 移除对 valueCache 的直接访问，从注册表获取数据
        val scripts = JSApi.getScriptsByApi(api.id)
        PacketDistributor.sendToAllPlayers(JSPayload(mapOf(api.id to scripts)))
    }

    /**
     * @deprecated 推荐使用 reloadScript() 进行单脚本重载
     */
    @Deprecated("Use reloadScript() for individual script reloading")
    fun reload(id: String) {
        validateApi(id)
        reload(JSApi.ALL[id]!!)
    }

    /**
     * @deprecated 推荐使用 reloadScript() 进行单脚本重载
     */
    @Deprecated("Use reloadScript() for individual script reloading")
    fun reloadAll() = JSApi.ALL.forEach { reload(it.value) }

}