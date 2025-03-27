package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
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
    normal: Matrix3f,
    buffer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
    color: Int,
    partialTick: Float
) {
    // 把坐标计算改在这里能解决omodel渲染卡顿的问题, 用接口中定义的却不行, 很奇怪
    val worldMatrix = Matrix4f()
    val worldPos = getWorldPosition(partialTick)
    worldMatrix.translate(worldPos.toVector3f())
    worldMatrix.rotateY(getRootYRot(partialTick))
    model.render(bones, worldMatrix, normal, buffer, packedLight, packedOverlay, color, partialTick)
}