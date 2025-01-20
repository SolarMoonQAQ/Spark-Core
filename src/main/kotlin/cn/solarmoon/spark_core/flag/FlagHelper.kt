package cn.solarmoon.spark_core.flag

import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity

fun Entity.getFlag(flag: Flag) = getData(SparkAttachments.FLAG).getOrDefault(flag, false)

fun Entity.putFlag(flag: Flag, boolean: Boolean) = getData(SparkAttachments.FLAG).put(flag, boolean)