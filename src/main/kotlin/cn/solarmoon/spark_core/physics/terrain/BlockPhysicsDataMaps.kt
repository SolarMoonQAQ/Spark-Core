package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.util.BlockCollisionUtil
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Block
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent

@EventBusSubscriber(modid = SparkCore.MOD_ID)
object BlockPhysicsDataMaps {

    @JvmField
    val BLOCK_PHYSICS_DATA: DataMapType<Block, BlockPhysicsData> = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "block_physics"),
        Registries.BLOCK,
        BlockPhysicsData.CODEC
    ).build()

    @SubscribeEvent
    fun register(event: RegisterDataMapTypesEvent) {
        event.register(BLOCK_PHYSICS_DATA)
    }

    // ---- 数据包热重载时清理缓存 ----

    /** 服务端重载 /reload 时清理 */
    @SubscribeEvent
    fun onAddReloadListener(event: AddReloadListenerEvent) {
        event.addListener(object : SimplePreparableReloadListener<Unit>() {
            override fun prepare(manager: net.minecraft.server.packs.resources.ResourceManager, profiler: ProfilerFiller) {
                BlockCollisionUtil.invalidatePhysicsCache()
            }

            override fun apply(unit: Unit, manager: net.minecraft.server.packs.resources.ResourceManager, profiler: ProfilerFiller) {
            }
        })
    }
}