package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkJSComponentRegisterEvent
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import net.neoforged.fml.util.LoaderException
import net.neoforged.neoforge.common.NeoForge
import org.mozilla.javascript.Context

abstract class SparkJS {

    @Volatile
    var context: Context? = null

    @Volatile
    var scope: org.mozilla.javascript.Scriptable? = null

    fun register() {
        NeoForge.EVENT_BUS.post(SparkJSComponentRegisterEvent(this))
    }

    fun eval(script: String, contextName: String = "") {
        runCatching {
            // 为当前线程创建或获取Context
            val currentContext = Context.enter()
            try {
                // 仅在 scope 尚未初始化时初始化标准对象，避免因 Context 不同导致覆盖已注入的组件
                if (scope == null) {
                    context = currentContext
                    scope = currentContext.initStandardObjects()
                }
                
                currentContext.evaluateString(scope, script, contextName, 1, null)
            } finally {
                Context.exit()
            }
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }

    fun validateApi(api: String) {
        if (!JSApi.ALL.contains(api)) throw NullPointerException("JS API $api 尚未注册！")
    }

    fun validateApi(api: JSApi) = validateApi(api.id)

}