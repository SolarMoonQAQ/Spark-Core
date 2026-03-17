package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.ModelPose
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix3f
import org.joml.Matrix4f

object ModelRenderHelper {
}

val tmpM4 = Matrix4f()
val tmpM3 = Matrix3f()

@JvmOverloads
fun OBone.render(
    pose: ModelPose,
    poseStack: PoseStack,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float,
    force: Boolean = false
) {
    tmpM4.identity()
    tmpM3.identity()
    applyTransformWithParents(pose, tmpM4, partialTick)
    poseStack.pushPose()
    poseStack.mulPose(tmpM4)
    // 渲染所有cubes
    for (cube in cubes) {
        cube.renderVertexes(
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            color,
            force
        )
    }
    
    // 渲染mesh（可为null）
    mesh?.renderVertexes(
        tmpM4,
        tmpM3,
        buffer,
        packedLight,
        packedOverlay,
        color,
        partialTick,
        force
    )
    poseStack.popPose()
}

@JvmOverloads
fun OModel.render(
    iBones: ModelPose,
    poseStack: PoseStack,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float,
    force: Boolean = false
) {
    bones.values.forEach {
        it.render(
            iBones,
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            color,
            partialTick,
            force
        )
    }
}


fun IAnimatable<*>.render(
    poseStack: PoseStack,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float
) {
    this.modelController.model?.let {
        it.origin.render(
            it.pose,
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            color,
            partialTick
        )
    }
}