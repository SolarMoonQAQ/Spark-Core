package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.sync.JSPayload
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import java.io.File
import kotlin.io.extension
import kotlin.io.readText

class ServerSparkJS: SparkJS() {

    /**
     * 动态加载对应api路径下的所有脚本
     */
    fun load(api: JSApi) {
        validateApi(api)
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val file = File(gameDir, "sparkcore/script/${api.id}")
        file.listFiles { it.extension == "js" }?.forEach {
            val value = it.readText()
            eval(value, it.path)
            api.valueCache[it.name] = value
            SparkCore.LOGGER.info("已加载脚本：模块：${api.id} 文件：${it.name}")
        }
        api.onLoad()
    }

    fun loadAll() = allApi.forEach { load(it.value) }

    /**
     * 动态重载对应api路径下的所有脚本并发送重载结果到客户端，客户端会进行一次[JSApi.onReload]
     */
    fun reload(api: JSApi) {
        validateApi(api)
        api.onReload()
        load(api)
        PacketDistributor.sendToAllPlayers(JSPayload(mapOf(api.id to api.valueCache), true))
    }

    fun reload(id: String) {
        validateApi(id)
        reload(allApi[id]!!)
    }

    fun reloadAll() = allApi.forEach { reload(it.value) }

}