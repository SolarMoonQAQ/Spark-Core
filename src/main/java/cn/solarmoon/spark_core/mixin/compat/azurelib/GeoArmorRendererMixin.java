package cn.solarmoon.spark_core.mixin.compat.azurelib;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.azure.azurelib.common.api.client.renderer.GeoArmorRenderer;
import mod.azure.azurelib.common.api.common.animatable.GeoItem;
import mod.azure.azurelib.common.internal.client.renderer.GeoRenderer;
import mod.azure.azurelib.common.internal.client.util.RenderUtils;
import mod.azure.azurelib.common.internal.common.cache.object.BakedGeoModel;
import mod.azure.azurelib.common.internal.common.cache.object.GeoBone;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(GeoArmorRenderer.class)
public abstract class GeoArmorRendererMixin<T extends Item & GeoItem> extends HumanoidModel implements GeoRenderer<T> {

    @Shadow protected Entity currentEntity;

    @Shadow protected GeoBone body;

    @Shadow protected GeoBone leftArm;

    @Shadow protected GeoBone rightArm;

    @Shadow protected GeoBone head;

    public GeoArmorRendererMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "preRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/Item;Lmod/azure/azurelib/common/internal/common/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V", at = @At("TAIL"))
    private void setArmorPivot(PoseStack poseStack, T animatable0, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        if (currentEntity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            if (body != null) {
                var pivot = animatable.getModelIndex().getModel().getBone("waist").getPivot().toVector3f().mul(16f);
                body.updatePivot(pivot.x, pivot.y, pivot.z);
            }
        }
    }

    @Inject(
            method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/Item;Lmod/azure/azurelib/common/internal/common/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At(value = "INVOKE", target = "Lmod/azure/azurelib/common/internal/client/renderer/GeoRenderer;renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lmod/azure/azurelib/core/animatable/GeoAnimatable;Lmod/azure/azurelib/common/internal/common/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V")
    )
    public void renderRecursively(PoseStack poseStack, T animatable0, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        if (currentEntity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            apply(leftArm, bone, poseStack);
            apply(rightArm, bone, poseStack);
            apply(head, bone, poseStack);
        }
    }

    private void apply(GeoBone bound, GeoBone bone, PoseStack poseStack) {
        if (body == null) return;
        if (bound == null) return;
        if (Objects.equals(bound.getName(), bone.getName())) {
            RenderUtils.prepMatrixForBone(poseStack, body);
        }
    }

}
