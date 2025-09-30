package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.AttackContext
import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.doc.JSClass

@JSClass("AttackContext")
interface JSAttackContext {

    val self get() = this as AttackContext

    fun js_isFirstAttack(): Boolean = self.isFirstAttack

    fun js_reset() = self.reset()

}