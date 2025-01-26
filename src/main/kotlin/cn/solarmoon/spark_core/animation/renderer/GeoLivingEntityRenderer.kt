package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

open class GeoLivingEntityRenderer<T>(context: EntityRendererProvider.Context, shadowRadius: Float):
    LivingEntityRenderer<T, EntityModel<T>>(context, EmptyModel(), shadowRadius), IGeoRenderer<T, T> where T : LivingEntity, T : IEntityAnimatable<T> {

    override val layers: MutableList<RenderLayer<T, T>> = mutableListOf()

    override fun render(
        animatable: T,
        yaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val physPartialTick = (animatable.level().getPhysLevel() as ClientPhysLevel).partialTicks.toFloat()
        super<LivingEntityRenderer>.render(animatable, yaw, partialTick, poseStack, bufferSource, packedLight)
        super<IGeoRenderer>.render(animatable, yaw, partialTick, physPartialTick, poseStack, bufferSource, packedLight)
    }

    override fun getTextureLocation(entity: T): ResourceLocation {
        return getGeoTextureLocation(entity)
    }

    override fun getOverlay(entity: T, partialTick: Float): Int {
        return getOverlayCoords(entity, getWhiteOverlayProgress(entity, partialTick))
    }

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