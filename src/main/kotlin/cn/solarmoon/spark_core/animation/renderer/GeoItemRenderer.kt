package cn.solarmoon.spark_core.animation.renderer

import cn.solarmoon.spark_core.animation.ICustomModelItem
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer
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
            val partialTicks = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
            val customModelItem = stack.item as ICustomModelItem
            val itemAnimatable: ItemAnimatable =
                customModelItem.getRenderInstance(stack, level, displayContext)
                    ?: return
            itemAnimatable.modelController.model?.let { model ->
                poseStack.pushPose()
                val pos = customModelItem.getRenderOffset(stack, level, displayContext)
                poseStack.translate(pos.x, pos.y, pos.z)
                val rot = customModelItem.getRenderRotation(stack, level, displayContext)
                poseStack.mulPose(Quaternionf().rotateZYX(rot.x, rot.y, rot.z))
                val scale = customModelItem.getRenderScale(stack, level, displayContext)
                poseStack.scale(scale.x, scale.y, scale.z)
                model.origin.render(
                    model.pose,
                    poseStack,
                    buffer.getBuffer(RenderType.entityTranslucent(model.textureLocation)),
                    Brightness.FULL_BRIGHT.pack(),
                    packedOverlay,
                    customModelItem.getColor(stack, level, displayContext).rgb,
                    partialTicks
                )
                poseStack.popPose()
            }
        }
    }

}