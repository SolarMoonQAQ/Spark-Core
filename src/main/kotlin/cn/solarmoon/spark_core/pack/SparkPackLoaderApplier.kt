package cn.solarmoon.spark_core.pack

import net.minecraft.client.resources.ClientPackSource
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.ResourceManager
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.event.AddPackFindersEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import java.util.Optional

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
object SparkPackLoaderApplier {
    val SPARK_PACK_LOCATION = PackLocationInfo(
        "spark_external_resources",
        Component.literal("Spark External Content"),
        PackSource.BUILT_IN,
        Optional.empty()
    )
    val CLIENT_PACK = SparkVirtualResourcePack(SPARK_PACK_LOCATION)

    @SubscribeEvent
    fun onServerReload(event: AddReloadListenerEvent) {
        event.addListener(server())
    }

    @SubscribeEvent
    fun onAddPackFinders(event: AddPackFindersEvent) {
        if (event.packType == PackType.CLIENT_RESOURCES) {
            // 预加载客户端资源
            SparkPackLoader.apply {
                initialize(true)
                readPackageGraph(true)
                readPackageContent(true, false)
                injectPackageContent(true, false)
            }
            // 添加为虚拟资源包
            event.addRepositorySource { consumer ->
                consumer.accept(
                    Pack.readMetaAndCreate(
                        SPARK_PACK_LOCATION,
                        ClientPackSource.fixedResources(CLIENT_PACK),
                        PackType.CLIENT_RESOURCES,
                        PackSelectionConfig(true, Pack.Position.BOTTOM, true)
                    )
                )
            }
        }
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