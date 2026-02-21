package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

open class GeoEntityRenderer<T>(context: EntityRendererProvider.Context): EntityRenderer<T>(context), IGeoRenderer<T, IEntityAnimatable<T>> where T : Entity, T : IEntityAnimatable<T> {

    override val layers: MutableList<RenderLayer<T, IEntityAnimatable<T>>> = mutableListOf()

    override fun render(
        entity: T,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        super<EntityRenderer>.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
        poseStack.pushPose()
        val pos = entity.getPosition(partialTick)
        poseStack.translate(-pos.x, -pos.y, -pos.z)
        poseStack.mulPose(Axis.YN.rotationDegrees(entity.getViewYRot(partialTick)))
        super<IGeoRenderer>.render(entity, partialTick, poseStack, bufferSource, packedLight)
        poseStack.popPose()
    }

    override fun getTextureLocation(entity: T): ResourceLocation {
        return getGeoTextureLocation(entity)
    }

}