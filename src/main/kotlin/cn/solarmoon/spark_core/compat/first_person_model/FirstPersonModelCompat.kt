package cn.solarmoon.spark_core.compat.first_person_model

import dev.tr7zw.firstperson.FirstPersonModelCore
import net.neoforged.fml.ModList

object FirstPersonModelCompat {
    const val MOD_ID = "firstperson"
    var IS_LOADED = false

    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }

    fun isActive() = IS_LOADED && FirstPersonModelCore.instance.isEnabled

}