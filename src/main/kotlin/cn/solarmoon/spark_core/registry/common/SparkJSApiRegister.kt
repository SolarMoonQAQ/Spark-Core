package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.extension.JSAnimHelper
import cn.solarmoon.spark_core.js.extension.JSDamageSourceHelper
import cn.solarmoon.spark_core.js.extension.JSEntityHelper
import cn.solarmoon.spark_core.js.extension.JSMath
import cn.solarmoon.spark_core.js.extension.JSPhysicsHelper
import cn.solarmoon.spark_core.js.put
import cn.solarmoon.spark_core.js.skill.JSSkillApi
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.registries.RegisterEvent

object SparkJSApiRegister {

    private fun reg(event: SparkJSRegisterEvent) {
        event.engine.scope.apply {
            put("SpMath", JSMath())
            put("SpDamageSourceHelper", JSDamageSourceHelper())
            put("SpEntityHelper", JSEntityHelper(event.engine))
            put("SpPhysicsHelper", JSPhysicsHelper(event.engine))
            put("SpAnimHelper", JSAnimHelper())
        }

        event.register(JSSkillApi(event.engine))
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}