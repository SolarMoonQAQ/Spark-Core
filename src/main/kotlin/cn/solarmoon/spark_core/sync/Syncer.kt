package cn.solarmoon.spark_core.sync

interface Syncer {

    val syncerType: SyncerType

    val syncData: SyncData

}