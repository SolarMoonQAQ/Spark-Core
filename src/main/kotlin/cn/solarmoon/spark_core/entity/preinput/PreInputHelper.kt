package cn.solarmoon.spark_core.entity.preinput

import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity

fun Entity.getPreInput() = getData(SparkAttachments.PREINPUT)