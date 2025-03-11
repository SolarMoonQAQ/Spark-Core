package cn.solarmoon.spark_core.compat.real_camera

import com.xtracr.realcamera.RealCameraCore
import net.neoforged.fml.ModList

object RealCameraCompat {

    fun isLoaded() = ModList.get().isLoaded("realcamera")

    fun isActive() = isLoaded() && RealCameraCore.isActive()

}