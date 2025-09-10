package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.extensions.JSAnimInstanceGlobal
import cn.solarmoon.spark_core.js.extensions.JSAttackSystemGlobal
import cn.solarmoon.spark_core.js.extensions.JSLoggerGlobal
import cn.solarmoon.spark_core.js.extensions.JSPhysicsCollisionObjectGlobal
import cn.solarmoon.spark_core.js.extensions.JSSkillConditionGlobal
import cn.solarmoon.spark_core.js.extensions.JSSkillGlobal
import cn.solarmoon.spark_core.js.modules.DefaultJSModule
import cn.solarmoon.spark_core.js.modules.SkillJSModule
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge

object SparkJSScriptRegister {

    private fun reg(event: SparkJSRegisterEvent) {
        event.registerModule(DefaultJSModule())
        event.registerModule(SkillJSModule())

        val runtime = event.runtime
        runtime.getBindings("js").apply {
            putMember("Logger", JSLoggerGlobal)
            putMember("AnimInstance", JSAnimInstanceGlobal)
            putMember("AttackSystem", JSAttackSystemGlobal)
            putMember("PhysicsCollisionObject", JSPhysicsCollisionObjectGlobal)
            putMember("SkillCondition", JSSkillConditionGlobal)
            putMember("Skill", JSSkillGlobal)
        }
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}