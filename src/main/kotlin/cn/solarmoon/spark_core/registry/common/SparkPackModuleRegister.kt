package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.pack.modules.*
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent

object SparkPackModuleRegister {

    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(ModelModule())
        event.register(AnimationModule())
        event.register(RecipeModule())
        event.register(LangModule())
        event.register(FontModule())
        event.register(TextureModule())
        event.register(AnimStateModule())
        event.register(AbilityTypeModule())
        event.register(SoundModule())
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}