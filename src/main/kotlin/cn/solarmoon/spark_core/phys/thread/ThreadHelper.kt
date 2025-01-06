package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.phys.IPhysLevelHolder
import net.minecraft.world.level.Level

fun Level.getPhysLevelById(id: String) = getAllPhysLevel()[id] ?: throw NullPointerException("未能找到名为 $id 的物理线程")

fun Level.getPhysLevel() = getPhysLevelById(SparkCore.MOD_ID)

fun Level.getAllPhysLevel() = (this as IPhysLevelHolder).allPhysLevel