package cn.solarmoon.spark_core.pack

import net.minecraft.client.resources.ClientPackSource
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.ServerPacksSource
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
    val CLIENT_PACK = SparkVirtualResourcePack()
    val DATA_PACK = SparkVirtualDataPack()

    /**
     * 重载时加载内容包数据
     */
    @SubscribeEvent
    fun onServerReload(event: AddReloadListenerEvent) {
        event.addListener(server())
    }

    /**
     * 初次启动而非重载时加载资源与数据
     */
    @SubscribeEvent
    fun onAddPackFinders(event: AddPackFindersEvent) {
        if (event.packType == PackType.CLIENT_RESOURCES) {
            // 预加载客户端资源
            SparkPackLoader.apply {
                initialize(true)
                readPackageGraph(true)
                readPackageContent(isClientSide = true, fromServer = false)
                injectPackageContent(isClientSide = true, fromServer = false)
            }
            // 添加为虚拟资源包
            event.addRepositorySource { consumer ->
                consumer.accept(
                    Pack.readMetaAndCreate(
                        CLIENT_PACK.location(),
                        ClientPackSource.fixedResources(CLIENT_PACK),
                        PackType.CLIENT_RESOURCES,
                        PackSelectionConfig(true, Pack.Position.BOTTOM, true)
                    )
                )
            }
        } else if (event.packType == PackType.SERVER_DATA) {
            // 添加为虚拟数据包
            event.addRepositorySource { consumer ->
                consumer.accept(
                    Pack.readMetaAndCreate(
                        DATA_PACK.location(),
                        ServerPacksSource.fixedResources(DATA_PACK),
                        PackType.SERVER_DATA,
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
                    readPackageContent(isClientSide = false, fromServer = true)
                }
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    injectPackageContent(isClientSide = false, fromServer = true)
                }
            }
        }

    fun client(): SimplePreparableReloadListener<Unit> =
        object : SimplePreparableReloadListener<Unit>() {

            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    initialize(true)
                    readPackageGraph(true)
                    readPackageContent(isClientSide = true, fromServer = false)
                }
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
                SparkPackLoader.apply {
                    injectPackageContent(isClientSide = true, fromServer = false)
                }
            }
        }

}