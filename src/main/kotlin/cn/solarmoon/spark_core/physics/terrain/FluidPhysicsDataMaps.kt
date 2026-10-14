package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.util.FluidCollisionUtil
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.material.Fluid
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent

/**
 * 流体物理属性的 DataMap 注册与缓存失效。
 *
 * 与 [BlockPhysicsDataMaps] 同构，区别在于目标是 `Registries.FLUID`，
 * 且失效时要重建的是 [FluidCollisionUtil] 的**无锁数组表**（物理线程会读它）。
 */
@EventBusSubscriber(modid = SparkCore.MOD_ID)
object FluidPhysicsDataMaps {

    @JvmField
    val FLUID_PHYSICS_DATA: DataMapType<Fluid, FluidPhysicsData> = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "fluid_physics"),
        Registries.FLUID,
        FluidPhysicsData.CODEC
    ).build()

    @SubscribeEvent
    fun register(event: RegisterDataMapTypesEvent) {
        event.register(FLUID_PHYSICS_DATA)
    }

    /**
     * 数据包热重载（`/reload`）时重建 [FluidCollisionUtil] 的属性表。
     *
     * 注意：reload 后 fluid 的**运行时 id 不保证稳定**，
     * 下游模组必须丢弃自己缓存的 fluidId（见 `section_fluid_index_plan.md` §10.6）。
     */
    @SubscribeEvent
    fun onAddReloadListener(event: AddReloadListenerEvent) {
        event.addListener(object : SimplePreparableReloadListener<Unit>() {
            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) {
                FluidCollisionUtil.invalidateFluidCache()
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
            }
        })
    }
}
