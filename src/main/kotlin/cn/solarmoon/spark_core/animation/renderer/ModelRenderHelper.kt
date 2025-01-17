package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix3f
import org.joml.Matrix4f

object ModelRenderHelper {
}

fun OBone.render(
    bones: BoneGroup,
    ma: Matrix4f,
    normal3f: Matrix3f,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float
) {
    applyTransformWithParents(bones, ma, partialTick)
    cubes.forEach {
        it.renderVertexes(Matrix4f(ma), normal3f, buffer, packedLight, packedOverlay, color)
    }
}

fun OModel.render(
    iBones: BoneGroup,
    matrix4f: Matrix4f,
    normal3f: Matrix3f,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float
) {
    bones.values.forEach {
        it.render(iBones, Matrix4f(matrix4f), normal3f, buffer, packedLight, packedOverlay, color, partialTick)
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
    model.render(bones, getWorldPositionMatrix(partialTick), poseStack.last().normal(), buffer, packedLight, packedOverlay, color, partialTick)
}