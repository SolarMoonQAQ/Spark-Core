package cn.solarmoon.spark_core.sync

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.level.Level

class SyncerType<T: Syncer>(
    private val provider: (Level, SyncData) -> T?
) {

    val registryKey get() = SparkRegistries.SYNCER_TYPE.getKey(this) ?: throw NullPointerException("同步体类型尚未注册")

    fun getSyncer(level: Level, syncData: SyncData): T? = provider(level, syncData)

    companion object {
        val CODEC = SparkRegistries.SYNCER_TYPE.byNameCodec()
        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.SYNCER_TYPE.key())
    }

}