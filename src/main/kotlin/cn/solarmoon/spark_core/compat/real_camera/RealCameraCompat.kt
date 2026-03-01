package cn.solarmoon.spark_core.compat.real_camera

import com.xtracr.realcamera.RealCameraCore
import net.neoforged.fml.ModList

object RealCameraCompat {
    const val MOD_ID = "realcamera"
    var IS_LOADED = false
    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }

    fun isActive() = IS_LOADED && RealCameraCore.isActive()

}