package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.neoforged.fml.ModList

object ARCompat {
    const val MOD_ID = "acceleratedrendering"
    var IS_LOADED = false

    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }

    fun renderCubeWithAR(
        cube: OCube,
        poseStack: PoseStack,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        return ARCompatImpl.renderCubeWithAR(cube, poseStack, buffer, light, overlay, color)
    }
}