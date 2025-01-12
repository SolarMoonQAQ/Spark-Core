package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import net.minecraft.resources.ResourceLocation

interface IPhysLevelHolder {

    val allPhysLevel: LinkedHashMap<ResourceLocation, PhysLevel>

}