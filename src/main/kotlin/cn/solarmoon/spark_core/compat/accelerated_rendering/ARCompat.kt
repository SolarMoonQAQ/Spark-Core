package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.neoforged.fml.ModList
import org.joml.Matrix3f
import org.joml.Matrix4f

object ARCompat {
    const val MOD_ID = "acceleratedrendering"
    var IS_LOADED = false

    fun init() {
        IS_LOADED = ModList.get().isLoaded(MOD_ID)
    }

    /** Cube级加速渲染入口 */
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

    /** 骨骼级加速渲染入口 —— 将整个骨骼合批渲染 */
    fun renderBoneWithAR(
        bone: OBone,
        transform: Matrix4f,
        normal: Matrix3f,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        return ARCompatImpl.renderBoneWithAR(bone, transform, normal, buffer, light, overlay, color)
    }
}