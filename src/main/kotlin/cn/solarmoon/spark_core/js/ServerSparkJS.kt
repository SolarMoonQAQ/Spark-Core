package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.sync.JSPayload
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import java.io.File
import java.nio.file.Files
import kotlin.io.extension
import kotlin.io.readText

class ServerSparkJS: SparkJS() {

    /**
     * 动态加载对应api路径下的所有脚本
     */
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
                api.valueCache[file.name] = value
                SparkCore.LOGGER.info("已加载脚本：模块：${api.id} 文件：${file.name}")
            }

        api.onLoad()
    }

    fun loadAll() = JSApi.ALL.forEach { load(it.value) }

    /**
     * 动态重载对应api路径下的所有脚本并发送重载结果到客户端，客户端会进行一次[JSApi.onReload]
     */
    fun reload(api: JSApi) {
        validateApi(api)
        api.onReload()
        load(api)
        PacketDistributor.sendToAllPlayers(JSPayload(mapOf(api.id to api.valueCache)))
    }

    fun reload(id: String) {
        validateApi(id)
        reload(JSApi.ALL[id]!!)
    }

    fun reloadAll() = JSApi.ALL.forEach { reload(it.value) }

}