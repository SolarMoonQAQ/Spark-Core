package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSComponentRegisterEvent
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import net.neoforged.fml.util.LoaderException
import net.neoforged.neoforge.common.NeoForge
import org.mozilla.javascript.Context

abstract class SparkJS {

    val context = Context.enter()

    val scope = context.initStandardObjects()

    fun register() {
        NeoForge.EVENT_BUS.post(SparkJSComponentRegisterEvent(this))
    }

    fun eval(script: String, contextName: String = "") {
        runCatching {
            context.evaluateString(scope, script, contextName, 1, null)
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }

    fun validateApi(api: String) {
        if (!JSApi.ALL.contains(api)) throw NullPointerException("JS API $api 尚未注册！")
    }

    fun validateApi(api: JSApi) = validateApi(api.id)

}