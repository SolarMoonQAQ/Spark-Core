package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.physics.terrain.FluidPhysicsData
import cn.solarmoon.spark_core.physics.terrain.FluidPhysicsDataMaps
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.material.Fluid

/**
 * 流体物理属性查询工具（物理线程安全）。
 * - [ensureBuilt] / [invalidateFluidCache]：仅主线程。
 * - [getFluidPhysicsData]：任意线程（含物理线程），只读。
 *
 * @see FluidPhysicsData
 */
object FluidCollisionUtil {

    @Volatile
    private var table: Array<FluidPhysicsData?> = emptyArray()

    /** 按全局 fluid id 查属性。越界或该流体无 DataMap 条目时返回 [FluidPhysicsData.DEFAULT]。 */
    fun getFluidPhysicsData(fluidId: Int): FluidPhysicsData {
        if (fluidId < 0) return FluidPhysicsData.DEFAULT
        val t = table
        return if (fluidId < t.size) (t[fluidId] ?: FluidPhysicsData.DEFAULT) else FluidPhysicsData.DEFAULT
    }

    /**
     * 重建属性表并整体替换引用。仅主线程调用（`/reload` 或首次构建流体索引时）。
     *
     * 重建后旧 id 可能已失效 —— 下游模组**必须**丢弃自己缓存的 fluidId
     * （见 `section_fluid_index_plan.md` §10.6）。
     */
    fun invalidateFluidCache() {
        table = buildTable()
    }

    /**
     * 幂等建表。仅主线程调用。
     *
     * 由 `SectionFluidIndex.Builder.build()` 在解析 palette 时调用，
     * 保证「物理线程拿到任何 fluidId 之前表已就绪」。
     */
    fun ensureBuilt() {
        if (table.isEmpty()) invalidateFluidCache()
    }

    private fun buildTable(): Array<FluidPhysicsData?> {
        val registry = BuiltInRegistries.FLUID
        val size = registry.size()
        if (size <= 0) return emptyArray()
        val arr = arrayOfNulls<FluidPhysicsData>(size)
        for (fluid: Fluid in registry) {
            val id = registry.getId(fluid)
            if (id !in 0..<size) continue
            arr[id] = fluid.builtInRegistryHolder().getData(FluidPhysicsDataMaps.FLUID_PHYSICS_DATA)
                ?: FluidPhysicsData.DEFAULT
        }
        return arr
    }
}
