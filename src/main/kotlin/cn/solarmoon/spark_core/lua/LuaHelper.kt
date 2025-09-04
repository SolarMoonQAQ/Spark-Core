package cn.solarmoon.spark_core.lua

import li.cil.repack.com.naef.jnlua.JavaFunction
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaStateFiveFour
import li.cil.repack.com.naef.jnlua.LuaType

fun selectLib(): String {
    val luaVersion = 54
    val osName = System.getProperty("os.name").lowercase()
    val archName = System.getProperty("os.arch").lowercase()

    val osPart = when {
        osName.contains("win") -> "windows"
        osName.contains("mac") || osName.contains("darwin") -> "darwin"
        osName.contains("nux") -> "linux"
        osName.contains("freebsd") -> "freebsd"
        else -> error("Unsupported OS: $osName")
    }

    val archPart = when {
        archName.contains("64") && (archName.contains("arm") || archName.contains("aarch")) -> "aarch64"
        archName.contains("64") -> "x86_64"
        archName.contains("86") -> "x86"
        else -> archName
    }

    val ext = when (osPart) {
        "windows" -> "dll"
        "darwin" -> "dylib"
        else -> "so"
    }

    return "libjnlua$luaVersion-$osPart-$archPart.$ext"
}

inline fun <reified T> LuaState.checkTable(index: Int): List<T> {
    checkType(index, LuaType.TABLE)
    val list = mutableListOf<T>()
    val len = rawLen(index)
    for (i in 1..len) {
        rawGet(index, i)
        val cond = checkJavaObject(-1, T::class.java)
        list.add(cond)
        pop(1)
    }
    return list
}

///**
// * 快捷调用 Lua 全局函数
// * @param funcName Lua 全局函数名
// * @param args 传入的参数（支持基础类型、ByteArray、JavaFunction、Java对象等）
// * @param results 期望返回值个数
// * @return 返回值列表（自动类型识别）
// */
//fun LuaState.execute(funcName: String, vararg args: Any?, results: Int = 0): List<Any?> {
//    // 获取全局函数
//    this.getGlobal(funcName)
//
//    // 压入参数
//    args.forEach { arg ->
//        when (arg) {
//            null -> pushNil()
//            is Boolean -> pushBoolean(arg)
//            is Int -> pushInteger(arg.toLong())
//            is Long -> pushInteger(arg)
//            is Double -> pushNumber(arg)
//            is Float -> pushNumber(arg.toDouble())
//            is String -> pushString(arg)
//            is ByteArray -> pushByteArray(arg)
//            is JavaFunction -> pushJavaFunction(arg)
//            else -> pushJavaObject(arg)
//        }
//    }
//
//    // 调用
//    call(args.size, results)
//
//    // 取返回值
//    val returnValues = mutableListOf<Any?>()
//    for (i in results downTo 1) {
//        val idx = -i
//        val value: Any? = when {
//            isBoolean(idx) -> toBoolean(idx)
//            isInteger(idx) -> toInteger(idx)
//            isNumber(idx) -> toNumber(idx)
//            isString(idx) -> toString(idx)
//            isNil(idx) || isNone(idx) || isNoneOrNil(idx) -> null
//            isTable(idx) -> toJavaObject(idx, Map::class.java) // 也可以转成 LuaTable
//            isJavaFunction(idx) -> toJavaObject(idx, JavaFunction::class.java)
//            isJavaObjectRaw(idx) -> toJavaObject(idx, Any::class.java)
//            else -> toJavaObject(idx, Any::class.java)
//        }
//        returnValues.add(value)
//    }
//
//    // 弹出返回值
//    if (results > 0) {
//        pop(results)
//    }
//
//    return returnValues
//}