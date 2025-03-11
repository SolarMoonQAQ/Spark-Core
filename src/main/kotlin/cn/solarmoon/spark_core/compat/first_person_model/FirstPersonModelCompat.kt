package cn.solarmoon.spark_core.compat.first_person_model

import dev.tr7zw.firstperson.FirstPersonModelCore
import net.neoforged.fml.ModList

object FirstPersonModelCompat {

    fun isLoaded() = ModList.get().isLoaded("firstperson")

    fun isActive() = isLoaded() && FirstPersonModelCore.instance.isEnabled

}