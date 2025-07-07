package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.ICustomModelItem
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
import cn.solarmoon.spark_core.registry.common.SparkCapabilities
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Brightness
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf


open class GeoItemRenderer: BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher, Minecraft.getInstance().entityModels), IGeoRenderer<ItemStack, ItemAnimatable> {

    override val layers: MutableList<RenderLayer<ItemStack, ItemAnimatable>> = mutableListOf()

    override fun renderByItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        if (stack.item is ICustomModelItem && Minecraft.getInstance().level!= null) {
            val level = Minecraft.getInstance().level!!
            val customModelItem = stack.item as ICustomModelItem
            val itemAnimatable: ItemAnimatable =
                customModelItem.getRenderInstance(stack, level, displayContext)
                    ?: return
            poseStack.pushPose()
            if (displayContext == ItemDisplayContext.GUI) poseStack.mulPose(Quaternionf().rotateY(Math.PI.toFloat()))
            itemAnimatable.model.render(
                itemAnimatable.bones,
                poseStack.last().pose()
                    .translate(customModelItem.getRenderOffset(stack, level, displayContext))
                    .rotateZYX(customModelItem.getRenderRotation(stack, level, displayContext))
                    .scale(customModelItem.getRenderScale(stack, level, displayContext)),
                poseStack.last().normal(),
                buffer.getBuffer(RenderType.entityTranslucent(itemAnimatable.modelIndex.textureLocation)),
                Brightness.FULL_BRIGHT.pack(),
                packedOverlay,
                customModelItem.getColor(stack, level, displayContext).getRGB(),
                itemAnimatable.partialTicks,
                true
            )
            poseStack.popPose()
        }
    }

}