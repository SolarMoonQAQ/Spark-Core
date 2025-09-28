package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.CollisionAttackSystem
import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("AttackSystem")
object JSAttackSystemGlobal {

    fun create(): CollisionAttackSystem = CollisionAttackSystem()

}