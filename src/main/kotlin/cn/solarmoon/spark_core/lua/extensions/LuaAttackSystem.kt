package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.lua.doc.LuaClass

@LuaClass("AttackSystem")
interface LuaAttackSystem {

    val self get() = this as AttackSystem

    fun getAttackedEntityIds() = self.attackedEntities

    fun isEmpty() = self.attackedEntities.isEmpty()

}