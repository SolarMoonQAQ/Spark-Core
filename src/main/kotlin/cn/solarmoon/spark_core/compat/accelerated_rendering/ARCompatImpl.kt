package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.github.argon4w.acceleratedrendering.core.CoreFeature
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix3f
import org.joml.Matrix4f

object ARCompatImpl {

    /**
     * Cube级加速渲染 —— 单个cube一个mesh，适用于需要逐个cube渲染的场景
     */
    fun renderCubeWithAR(
        cube: OCube,
        poseStack: PoseStack,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        val extension = VertexConsumerExtension.getAccelerated(buffer)
        val pose = poseStack.last()
        if (AcceleratedEntityRenderingFeature.isEnabled()
            && AcceleratedEntityRenderingFeature.shouldUseAcceleratedPipeline()
            && (CoreFeature.isRenderingLevel() || (CoreFeature.isRenderingGui() && AcceleratedEntityRenderingFeature.shouldAccelerateInGui()))
            && extension.isAccelerated
        ) {
            extension.doRender(
                ARCubeRenderer,
                cube,
                pose.pose(),
                pose.normal(),
                light,
                overlay,
                color
            )
            return true
        } else {
            return false
        }
    }

    /**
     * 骨骼级加速渲染 —— 将整个骨骼的cube和mesh合批为单个mesh，一次draw call
     * @param bone 要渲染的骨骼
     * @param transform 骨骼的世界变换矩阵（已包含父骨骼+动画变换）
     * @param normal 骨骼的法线变换矩阵
     */
    fun renderBoneWithAR(
        bone: OBone,
        transform: Matrix4f,
        normal: Matrix3f,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        val extension = VertexConsumerExtension.getAccelerated(buffer)
        if (AcceleratedEntityRenderingFeature.isEnabled()
            && AcceleratedEntityRenderingFeature.shouldUseAcceleratedPipeline()
            && (CoreFeature.isRenderingLevel())
            && extension.isAccelerated
        ) {
            extension.doRender(ARBoneRenderer, bone, transform, normal, light, overlay, color)
            return true
        }
        return false
    }
}