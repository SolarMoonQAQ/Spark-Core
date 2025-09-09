package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.resource2.modules.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object SparkPackModuleRegister {

    val recipe = RecipeModule()
    val lang = LangModule()
    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(ModelModule())
        event.register(AnimationModule())
        event.register(LuaScriptModule())
        event.register(recipe)
        event.register(lang)
        event.register(TextureModule())
    }

    @SubscribeEvent
    @JvmStatic
    fun regReloadListener(event: RegisterClientReloadListenersEvent) {
        //注册reload监听器以确保原版进行reload时重新注入外部包内容
        event.registerReloadListener(lang)
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}