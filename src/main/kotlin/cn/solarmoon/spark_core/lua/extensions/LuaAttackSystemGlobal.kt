package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.lua.doc.LuaGlobal

@LuaGlobal("AttackSystem")
object LuaAttackSystemGlobal {

    fun create() = AttackSystem()

}