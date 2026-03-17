package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.github.argon4w.acceleratedrendering.core.CoreFeature
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer

object ARCompatImpl {
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
}