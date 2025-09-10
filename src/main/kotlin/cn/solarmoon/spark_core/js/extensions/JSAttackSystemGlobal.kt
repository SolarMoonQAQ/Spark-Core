package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("AttackSystem")
object JSAttackSystemGlobal {

    fun create(): AttackSystem = AttackSystem()

}