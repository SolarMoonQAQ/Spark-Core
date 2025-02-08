package cn.solarmoon.spark_core.sync

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

class EntitySyncerType: SyncerType() {
    override fun getSyncer(level: Level, syncData: SyncData): Syncer? {
        return (syncData.data as? Int)?.let { level.getEntity(it) }
    }
}