package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f

open class GeoLivingEntityRenderer<T>(context: EntityRendererProvider.Context, shadowRadius: Float):
    LivingEntityRenderer<T, EntityModel<T>>(context, EmptyModel(), shadowRadius),
    IGeoRenderer<T> where T : LivingEntity, T : IEntityAnimatable<T> {

    val layers = arrayListOf<RenderLayer<T>>()

    override fun render(
        entity: T,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
        if (entity.isInvisible) return
        val buffer = bufferSource.getBuffer(getRenderType(entity))
        val animData = entity.modelData
        val model = animData.model
        poseStack.pushPose()
        val overlay = getOverlayCoords(entity, getWhiteOverlayProgress(entity, partialTick))
        val matrix = Matrix4f().translate(entity.getPosition(partialTick).toVector3f()).rotateY(entity.getRootYRot(partialTick))
        preRender(entity, entityYaw, matrix, partialTick, poseStack, bufferSource, packedLight)
        model.renderBones(entity, matrix, poseStack.last().normal(), buffer, packedLight, overlay, getColor(entity), partialTick)
        postRender(entity, entityYaw, matrix, partialTick, poseStack, bufferSource, packedLight)
        poseStack.popPose()

        layers.forEach { it.render(entity, partialTick, poseStack, bufferSource, packedLight, -1) }
    }
    
    open fun getColor(entity: T): Int = -1

    open fun getRenderType(entity: T): RenderType {
        return RenderType.entityTranslucent(getTextureLocation(entity))
    }

    override fun getTextureLocation(entity: T): ResourceLocation {
        return entity.modelData.textureLocation
    }

    /**
     * 可在渲染之前调整一些额外的变换
     */
    open fun preRender(entity: T, entityYaw: Float, matrix: Matrix4f, partialTick: Float, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {}

    /**
     * 可在渲染之后渲染额外内容
     */
    open fun postRender(entity: T, entityYaw: Float, matrix: Matrix4f, partialTick: Float, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {}

    // 下面无用
    class EmptyModel<T: Entity>: EntityModel<T>() {
        override fun setupAnim(
            entity: T,
            limbSwing: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
            netHeadYaw: Float,
            headPitch: Float
        ) {
        }

        override fun renderToBuffer(
            poseStack: PoseStack,
            buffer: VertexConsumer,
            packedLight: Int,
            packedOverlay: Int,
            color: Int
        ) {
        }
    }

}