package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.AttackContext
import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("AttackContext")
object JSAttackContextGlobal {

    fun create(): AttackContext = AttackContext()

}