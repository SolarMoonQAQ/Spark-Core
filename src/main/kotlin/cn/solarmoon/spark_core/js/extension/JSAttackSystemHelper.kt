package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.JSComponent

object JSAttackSystemHelper: JSComponent() {

    fun create() = AttackSystem()

}