package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js2.JavaScript
import cn.solarmoon.spark_core.js2.SparkJSLoader
import cn.solarmoon.spark_core.js2.modules.DefaultJSModule
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import net.minecraft.resources.ResourceLocation

class ScriptModule: SparkPackModule {

    override val id: String = "scripts"

    override fun onStart() {
        SparkJSLoader.initialize()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        val jsModuleId = pathSegments.getOrNull(0) ?: DefaultJSModule.ID
        if (!SparkJSLoader.modules.contains(jsModuleId)) throw IllegalArgumentException("未找到脚本模块: $jsModuleId，请保证脚本放在存在的模块文件中，比如 scripts/skill/test.js 代表skill模块，如若该脚本不依赖任何模块，可以放入default文件夹或直接留在script根目录")
        val js = JavaScript(ResourceLocation.fromNamespaceAndPath(jsModuleId, fileName.removeSuffix(".js")), content)
        SparkJSLoader.load(js)
    }

    override fun onFinish() {
        val grouped = SparkJSLoader.scripts.keys.groupBy { it.namespace }
        val logMsg = buildString {
            append("\n📰成功加载 ${grouped.size} 个脚本模块，共 ${SparkJSLoader.scripts.size} 个脚本📰\n")
            grouped.forEach { (namespace, ids) ->
                append("✅$namespace: [")
                append(ids.joinToString(", ") { it.toString() })
                append("]\n")
            }
        }
        SparkCore.logger("脚本加载器").info(logMsg)
    }


}