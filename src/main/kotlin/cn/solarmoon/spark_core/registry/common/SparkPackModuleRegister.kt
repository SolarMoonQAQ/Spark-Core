package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.pack.modules.*
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent

object SparkPackModuleRegister {

    val recipe = RecipeModule()
    val lang = LangModule()

    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(ModelModule())
        event.register(AnimationModule())
        event.register(recipe)
        event.register(lang)
        event.register(TextureModule())
        event.register(AnimStateModule())
        event.register(AbilityTypeModule())
    }

    fun regReloadListener(event: RegisterClientReloadListenersEvent) {
        //注册reload监听器以确保原版进行reload时重新注入外部包内容
        event.registerReloadListener(lang)
        event.registerReloadListener(recipe)
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
        bus.addListener(::regReloadListener)
    }

}