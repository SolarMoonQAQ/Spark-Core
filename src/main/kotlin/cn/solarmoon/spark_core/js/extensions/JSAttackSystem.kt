package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.doc.JSClass

@JSClass("AttackSystem")
interface JSAttackSystem {

    val self get() = this as AttackSystem

    fun js_isFirstAttack(): Boolean = self.isFirstAttack

    fun js_reset() = self.reset()

}