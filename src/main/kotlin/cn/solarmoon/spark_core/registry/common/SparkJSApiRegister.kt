package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkJSComponentRegisterEvent
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.extension.JSAnimHelper
import cn.solarmoon.spark_core.js.extension.JSDamageSourceHelper
import cn.solarmoon.spark_core.js.extension.JSEntityHelper
import cn.solarmoon.spark_core.js.extension.JSLogger
import cn.solarmoon.spark_core.js.extension.JSMath
import cn.solarmoon.spark_core.js.extension.JSPhysicsHelper
import cn.solarmoon.spark_core.js.ik.JSIKApi
import cn.solarmoon.spark_core.js.put
import cn.solarmoon.spark_core.js.skill.JSSkillApi
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.registries.RegisterEvent

object SparkJSApiRegister {

    private fun r(event: FMLCommonSetupEvent) {
        JSApi.register()
    }

    private fun reg(event: SparkJSRegisterEvent) {
        event.register(JSSkillApi)
    }

    private fun regCom(event: SparkJSComponentRegisterEvent) {
        event.registerComponent("Skill", JSSkillApi)
        event.registerComponent("SpMath", JSMath)
        event.registerComponent("DamageSourceHelper", JSDamageSourceHelper)
        event.registerComponent("EntityHelper", JSEntityHelper)
        event.registerComponent("AnimHelper", JSAnimHelper)
        event.registerComponent("PhysicsHelper", JSPhysicsHelper)
        event.registerComponent("Logger", JSLogger)
        event.registerComponent("Ik", JSIKApi)
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::r)
        bus.addListener(::reg)
        NeoForge.EVENT_BUS.addListener(::regCom)
    }

}