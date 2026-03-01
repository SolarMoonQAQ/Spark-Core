package cn.solarmoon.spark_core.compat.sodium

import net.neoforged.fml.ModList

object SodiumCompat {
    const val MOD_ID = "embeddium"
    var IS_LOADED = false;

    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }
}