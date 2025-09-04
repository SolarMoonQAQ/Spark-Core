package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js2.modules.DefaultJSModule
import cn.solarmoon.spark_core.lua.LuaScript
import cn.solarmoon.spark_core.lua.SparkLua
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import net.minecraft.resources.ResourceLocation

class LuaScriptModule: SparkPackModule {

    override val id: String = "lua_scripts"

    override fun onStart() {
        SparkLua.initialize()
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (pathSegments.lastOrNull() == "mention") return // 注释文件
        val luaModuleId = pathSegments.getOrNull(0) ?: DefaultJSModule.ID
        if (!SparkLua.modules.contains(luaModuleId)) throw IllegalArgumentException("未找到脚本模块: $luaModuleId，请保证脚本放在存在的模块文件中，比如 scripts/skill/test.lua 代表skill模块，如若该脚本不依赖任何模块，可以放入default文件夹或直接留在lua_scripts根目录")
        val lua = LuaScript(ResourceLocation.fromNamespaceAndPath(luaModuleId, fileName.removeSuffix(".lua")), content)
        SparkLua.load(lua)
    }

    override fun onFinish() {
        val grouped = SparkLua.scripts.keys.groupBy { it.namespace }
        val logMsg = buildString {
            append("\n📰成功加载 ${grouped.size} 个脚本模块，共 ${SparkLua.scripts.size} 个脚本📰\n")
            grouped.forEach { (namespace, ids) ->
                append("✅$namespace: [")
                append(ids.joinToString(", ") { it.toString() })
                append("]\n")
            }
        }
        SparkCore.logger("Lua脚本加载器").info(logMsg)
    }


}