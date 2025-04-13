package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import net.neoforged.fml.ModLoader

interface JSApi {

    companion object {
        lateinit var ALL: Map<String, JSApi>
            private set

        var clientApiCache = mapOf<String, Map<String, String>>()
            internal set(value) {
                SparkCore.LOGGER.info("已更新脚本默认数据")
                field = value
            }

        fun register() {
            val regs = mutableSetOf<JSApi>()
            ModLoader.postEvent(SparkJSRegisterEvent(regs))
            ALL = buildMap { regs.forEach { put(it.id, it) } }
            SparkCore.LOGGER.info("已注册脚本模块：${ALL.keys}")
        }
    }

    val id: String

    val valueCache: MutableMap<String, String>

    /**
     * 当api的所有脚本加载完毕后调用
     */
    fun onLoad()

    /**
     * 当api的脚本重载时调用
     */
    fun onReload()

}