package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Shadow public abstract ResourceLocation getTextureLocation(AbstractClientPlayer entity);

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void render(AbstractClientPlayer entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        var animatable = entity;
        var model = animatable.getModelController().getModel();
        if (model == null) return;
        var modelIndex = model.getIndex();
        // 使用SparkResourcePathBuilder检查是否是默认player模型路径
        if (!modelIndex.isPlayer()) {
            var vb = buffer.getBuffer(RenderType.entityTranslucent(animatable.getModelController().getTextureLocation()));
            ModelRenderHelperKt.render(animatable, poseStack.last().normal(), vb, packedLight, getOverlayCoords(entity, getWhiteOverlayProgress(entity, partialTicks)), -1, partialTicks);
            ci.cancel();
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("HEAD"), cancellable = true)
    private void rot(AbstractClientPlayer entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (VanillaModelHelper.shouldSwitchToAnim(entity)) {
            super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
            ci.cancel();
        }
    }

}
