package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkJSComponentRegisterEvent
import cn.solarmoon.spark_core.event.SparkJSRegisterEvent
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.extension.*
import cn.solarmoon.spark_core.js.extension.JSResourcePath
import cn.solarmoon.spark_core.js.ik.JSIKApi
import cn.solarmoon.spark_core.js.resource.JSResourcePathApi
import cn.solarmoon.spark_core.js.skill.JSSkillApi
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge

object SparkJSApiRegister {

    private fun r(event: FMLCommonSetupEvent) {
        JSApi.register()
    }

    private fun reg(event: SparkJSRegisterEvent) {
        event.register(JSSkillApi)
        event.register(JSIKApi)  // 修复：添加JSIKApi的注册
        event.register(JSResourcePathApi)  // 添加资源路径API
    }

    private fun regCom(event: SparkJSComponentRegisterEvent) {
        event.registerComponent("Skill", JSSkillApi)
        event.registerComponent("DamageSourceHelper", JSDamageSourceHelper)
        event.registerComponent("AnimHelper", JSAnimHelper)
        event.registerComponent("PhysicsHelper", JSPhysicsHelper)
        event.registerComponent("AttackSystem", JSAttackSystemHelper)
        event.registerComponent("Logger", JSLogger)
        event.registerComponent("Ik", JSIKApi)
        event.registerComponent("ResourcePath", JSResourcePath)  // 添加资源路径组件
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::r)
        bus.addListener(::reg)
        NeoForge.EVENT_BUS.addListener(::regCom)
    }

}