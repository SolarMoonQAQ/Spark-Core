package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent
import kotlin.collections.component1
import kotlin.collections.component2

object SparkJsApplier {

    @SubscribeEvent
    private fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as Level
        val js:SparkJS = level.jsEngine
        js.register()
        if (js is ServerSparkJS) js.loadAll()
        else {
            JSApi.clientApiCache.forEach { apiId, v ->
                js.validateApi(apiId)
                val api = JSApi.ALL[apiId]!!
                v.forEach {
                    val (fileName, value) = it
                    js.eval(value, "$apiId - $fileName")
                    api.valueCache[fileName] = value
                    SparkCore.LOGGER.info("已加载服务器脚本默认数据：模块：$apiId 文件：$fileName")
                }
                api.onLoad()
            }
        }
    }

}