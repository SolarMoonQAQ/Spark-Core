package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaGlobal

@LuaGlobal("List")
object LuaListGlobal {

    fun of(args: Array<*>) = args.toList()

}