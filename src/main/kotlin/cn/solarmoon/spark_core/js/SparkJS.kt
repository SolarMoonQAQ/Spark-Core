package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import net.neoforged.fml.util.LoaderException
import net.neoforged.neoforge.common.NeoForge
import org.mozilla.javascript.Context

abstract class SparkJS {

    companion object {
        val ALL = mutableMapOf<Boolean, SparkJS>()
    }

    lateinit var allApi: Map<String, JSApi>
        private set

    val context = Context.enter()

    val scope = context.initStandardObjects()

    fun register() {
        val regs = mutableSetOf<JSApi>()
        NeoForge.EVENT_BUS.post(SparkJSRegisterEvent(this, regs))
        allApi = buildMap { regs.forEach { put(it.id, it) } }
        allApi.values.forEach {
            it.onRegister(this)
        }
        SparkCore.LOGGER.info("已注册脚本模块：${allApi.keys}")
    }

    fun eval(script: String, contextName: String = "") {
        runCatching {
            context.evaluateString(scope, script, contextName, 1, null)
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }

    fun validateApi(api: String) {
        if (!allApi.contains(api)) throw NullPointerException("JS API $api 尚未注册！")
    }

    fun validateApi(api: JSApi) = validateApi(api.id)

}