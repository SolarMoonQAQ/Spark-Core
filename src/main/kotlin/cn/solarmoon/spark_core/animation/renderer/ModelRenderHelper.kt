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
    poseMatrix: Matrix4f,
    poseNormal: Matrix3f,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float,
    force: Boolean = false
) {
    tmpM4.set(poseMatrix)
    tmpM3.set(poseNormal)
    // ===== 顶点矩阵 =====
    applyTransformWithParents(pose, tmpM4, partialTick)

    // ===== Bone 法线矩阵 =====
    applyNormalTransformWithParents(pose, tmpM3, partialTick)
    for (cube in cubes) {
        cube.renderVertexes(
            tmpM4,
            tmpM3,
            buffer,
            packedLight,
            packedOverlay,
            color,
            force
        )
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
            Matrix3f(normal3f),
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