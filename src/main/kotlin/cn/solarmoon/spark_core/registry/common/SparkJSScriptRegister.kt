package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.extensions.JSAnimInstanceGlobal
import cn.solarmoon.spark_core.js.extensions.JSAttackSystemGlobal
import cn.solarmoon.spark_core.js.extensions.JSLoggerGlobal
import cn.solarmoon.spark_core.js.extensions.JSPhysicsCollisionObjectGlobal
import cn.solarmoon.spark_core.js.extensions.JSSkillConditionGlobal
import cn.solarmoon.spark_core.js.extensions.JSSkillGlobal
import cn.solarmoon.spark_core.js.modules.DefaultJSModule
import cn.solarmoon.spark_core.js.modules.SkillJSModule
import cn.solarmoon.spark_core.js.put
import net.neoforged.bus.api.IEventBus

object SparkJSScriptRegister {

    private fun reg(event: SparkJSRegisterEvent) {
        event.registerModule(DefaultJSModule())
        event.registerModule(SkillJSModule())

        event.scriptable.apply {
            put("Logger", JSLoggerGlobal)
            put("AnimInstance", JSAnimInstanceGlobal)
            put("AttackSystem", JSAttackSystemGlobal)
            put("PhysicsCollisionObject", JSPhysicsCollisionObjectGlobal)
            put("SkillCondition", JSSkillConditionGlobal)
            put("Skill", JSSkillGlobal)
        }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}