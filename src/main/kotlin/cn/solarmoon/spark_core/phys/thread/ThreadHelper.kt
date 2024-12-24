package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.phys.IPhysWorldHolder
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.neoforge.attachment.IAttachmentHolder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

fun Level.getPhysWorld() = (this as IPhysWorldHolder).physWorld

fun Entity.getPhysWorld() = level().getPhysWorld()