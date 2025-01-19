package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity


fun Entity.pushAttackedData(data: AttackedData) {
    (this as IAttackedDataPusher).data = data
}

fun Entity.updateAttackedData(updater: AttackedData.() -> Unit) {
    (this as IAttackedDataPusher).data?.let { updater.invoke(it) }
}

fun Entity.getAttackedData() = (this as IAttackedDataPusher).data

fun Entity.resetAttackedData() {
    (this as IAttackedDataPusher).data = null
}

fun DamageSource.getExtraData() = (this as IExtraDamageDataHolder).data