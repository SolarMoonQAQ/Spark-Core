package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
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

        /**
         * 从动态注册表获取指定API的所有脚本
         */
        fun getScriptsByApi(apiId: String): Map<String, String> {
            return try {
                SparkRegistries.JS_SCRIPTS.getDynamicEntries()
                    .values
                    .filter { it.apiId == apiId }
                    .associate { it.fileName to it.content }
            } catch (e: Exception) {
                SparkCore.LOGGER.debug("获取API {}的脚本时出错: {}", apiId, e.message)
                emptyMap()
            }
        }

        /**
         * 获取所有API的脚本数据，用于客户端同步
         */
        fun getAllScriptsForSync(): Map<String, Map<String, String>> {
            return try {
                SparkRegistries.JS_SCRIPTS.getDynamicEntries()
                    .values
                    .groupBy { it.apiId }
                    .mapValues { (_, scripts) ->
                        scripts.associate { it.fileName to it.content }
                    }
            } catch (e: Exception) {
                SparkCore.LOGGER.debug("获取所有脚本数据时出错: {}", e.message)
                emptyMap()
            }
        }
    }

    val id: String

    /**
     * 脚本缓存，默认从动态注册表计算获取
     * 实现类可以选择override使用本地缓存，或者依赖默认的动态注册表实现
     */
    val valueCache: MutableMap<String, String>
        get() = getScriptsByApi(id).toMutableMap()

    /**
     * 获取属于当前API的脚本数量
     */
    val scriptCount: Int
        get() = getScriptsByApi(id).size

    /**
     * 当api的所有脚本加载完毕后调用
     */
    fun onLoad()

    /**
     * 当api的脚本重载时调用
     */
    fun onReload()

}