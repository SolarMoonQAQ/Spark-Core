package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JavaScript
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.modules.DefaultJSModule
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment

class JSScriptModule: SparkPackModule {

    override val id: String = "js_scripts"

    override fun onInitialize(isClientSide: Boolean) {
        SparkJS.get().initialize()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean
    ) {
        val js = SparkJS.get()
        val moduleId = pathSegments.getOrNull(0) ?: DefaultJSModule.ID
        if (!js.modules.contains(moduleId)) throw IllegalArgumentException("未找到脚本模块: $moduleId，请保证脚本放在存在的模块文件中，比如 scripts/skill/test.js 代表skill模块，如若该脚本不依赖任何模块，可以放入default文件夹或直接留在 $id 根目录")
        val lua = JavaScript(ResourceLocation.fromNamespaceAndPath(moduleId, fileName.removeSuffix(".js")), content)
        js.load(lua)
    }

    override fun onFinish(isClientSide: Boolean) {
        val js = SparkJS.get()
        val grouped = js.scripts.keys.groupBy { it.namespace }
        val logMsg = buildString {
            append("\n📰成功加载 ${grouped.size} 个脚本模块，共 ${js.scripts.size} 个脚本📰\n")
            grouped.forEach { (namespace, ids) ->
                append("✅$namespace: [")
                append(ids.joinToString(", ") { it.path })
                append("]\n")
            }
        }
        SparkCore.logger("JS脚本加载器").info(logMsg)
    }


}