package cn.solarmoon.spark_core.sync

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.world.level.Level

abstract class SyncerType {

    val registryKey get() = SparkRegistries.SYNCER_TYPE.getKey(this) ?: throw NullPointerException("同步体类型尚未注册")

    abstract fun getSyncer(level: Level, syncData: SyncData): Syncer?

    override fun equals(other: Any?): Boolean {
        return (other as? SyncerType)?.registryKey == registryKey
    }

    override fun hashCode(): Int {
        return registryKey.hashCode()
    }

}