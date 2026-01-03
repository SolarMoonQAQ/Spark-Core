package cn.solarmoon.spark_core.pack

import net.minecraft.server.packs.resources.ResourceManager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
object SparkPackLoaderApplier {

    @SubscribeEvent
    fun onServerReload(event: AddReloadListenerEvent) {
        event.addListener(server())
    }

    /**
     * 通过SparkCommonEventRegister注册
     */
    fun onClientReload(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener(client())
    }

    fun server(): SimplePreparableReloadListener<Unit> =
        object : SimplePreparableReloadListener<Unit>() {

            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    initialize(false)
                    readPackageGraph(false)
                    readPackageContent(false, true)
                }
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    injectPackageContent(false, true)
                }
            }
        }

    fun client(): SimplePreparableReloadListener<Unit> =
        object : SimplePreparableReloadListener<Unit>() {

            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    initialize(true)
                    readPackageGraph(true)
                    readPackageContent(true, false)
                }
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    injectPackageContent(true, false)
                }
            }
        }

}