package cn.solarmoon.spark_core.entity.attack

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.awt.Color
import java.util.Optional


fun Entity.setAttackedData(data: AttackedData) {
    setData(SparkAttachments.ATTACKED_DATA, Optional.of(data))
}

fun Entity.getAttackedData(): AttackedData? {
    return getData(SparkAttachments.ATTACKED_DATA).orElse(null)
}

fun Entity.clearAttackedData() {
    setData(SparkAttachments.ATTACKED_DATA, Optional.empty())
}