package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.ModelPose
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.compat.accelerated_rendering.ARCompat
import cn.solarmoon.spark_core.compat.sodium.SodiumCompat
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
    applyNormalTransformWithParents(pose, tmpM3, partialTick)

    // AR / Sodium 骨骼级路径需要合并 PoseStack 上已有的实体世界变换
    // 回退路径通过 poseStack.mulPose(tmpM4) 自然合并，但AR/Sodium跳过了PoseStack，
    // 必须手动合成：worldTransform = entityPose × boneLocal
    val poseEntry = poseStack.last()
    val worldM4 = Matrix4f(poseEntry.pose()).mul(tmpM4)
    val worldM3 = Matrix3f(poseEntry.normal()).mul(tmpM3)

    // 优先使用骨骼级加速渲染 —— 将整个骨骼的cube和mesh合批为单个mesh，一次draw call
    // force参数在AR路径中无效（面剔除交由AR管线处理），非AR回退路径仍遵循force语义
    if (ARCompat.IS_LOADED && ARCompat.renderBoneWithAR(
            this, worldM4, worldM3, buffer, packedLight, packedOverlay, color
        )
    ) {
        return
    }

//    // 次级：Sodium / Embeddium 顶点缓冲快写 —— 合批后一次提交，无逐顶点JNI开销 TODO: 位姿不对，需要排查
//    if (SodiumCompat.IS_LOADED && SodiumCompat.renderBone(
//            this, worldM4, worldM3, buffer, packedLight, packedOverlay, color
//        )
//    ) {
//        return
//    }

    // 回退：逐个cube渲染（含ARCubeRenderer的cube级加速 + 传统立即模式）
    poseStack.pushPose()
    poseStack.mulPose(tmpM4)
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