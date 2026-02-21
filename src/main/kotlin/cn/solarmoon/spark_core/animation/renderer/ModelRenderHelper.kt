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

@JvmOverloads
fun OBone.render(
    bones: ModelPose,
    ma: Matrix4f,
    normal3f: Matrix3f,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float,
    force: Boolean = false
) {
    applyTransformWithParents(bones, ma, partialTick)
    cubes.forEach {
        it.renderVertexes(Matrix4f(ma), normal3f, buffer, packedLight, packedOverlay, color, force)
    }
}

@JvmOverloads
fun OModel.render(
    iBones: ModelPose,
    matrix4f: Matrix4f,
    normal3f: Matrix3f,
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
            Matrix4f(matrix4f),
            normal3f,
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
            poseStack.last().pose(),
            poseStack.last().normal(),
            buffer,
            packedLight,
            packedOverlay,
            color,
            partialTick
        )
    }
}