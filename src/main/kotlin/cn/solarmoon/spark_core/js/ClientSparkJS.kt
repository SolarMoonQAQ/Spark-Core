package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.origin.OJSScript
import net.minecraft.client.Minecraft

class ClientSparkJS: SparkJS() {

    /**
     * 客户端脚本执行 - 在渲染线程执行
     */
    override fun executeScript(script: OJSScript) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.isSameThread) {
            // 已在渲染线程，直接执行
            super.executeScript(script)
        } else {
            // 切换到渲染线程执行
            minecraft.execute {
                super.executeScript(script)
            }
        }
    }

    /**
     * 客户端重新加载脚本
     */
    override fun reloadScript(script: OJSScript) {
        super.reloadScript(script)
        SparkCore.LOGGER.debug("客户端重新加载脚本: API=${script.apiId}, 文件=${script.fileName}")
    }

    /**
     * 客户端卸载脚本
     */
    override fun unloadScript(apiId: String, fileName: String) {
        super.unloadScript(apiId, fileName)
        SparkCore.LOGGER.debug("客户端卸载脚本: API=$apiId, 文件=$fileName")
    }

}