package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.phys.IPhysLevelHolder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

fun Level.getPhysLevelById(id: ResourceLocation) = getAllPhysLevel()[id] ?: throw NullPointerException("未能找到名为 $id 的物理线程")

fun Level.getPhysLevel() = getPhysLevelById(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "main"))

fun Level.getAllPhysLevel() = (this as IPhysLevelHolder).allPhysLevel

fun Level.laterConsume(action: () -> Unit) {
    (this as ILaterConsumerHolder).consumers.add(action)
}