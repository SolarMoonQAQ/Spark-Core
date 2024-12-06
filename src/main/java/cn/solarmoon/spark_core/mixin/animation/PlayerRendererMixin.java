package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.api.animation.model.CommonModel;
import cn.solarmoon.spark_core.api.animation.vanilla.PlayerAnimHelper;
import cn.solarmoon.spark_core.api.phys.thread.ClientPhysLevel;
import cn.solarmoon.spark_core.api.phys.thread.ThreadHelperKt;
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
    private void render(AbstractClientPlayer entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        var animatable = PlayerAnimHelper.getAnimatable(entity);
        var animData = animatable.getAnimData();
        var path = animData.getModelPath();
        if (!path.equals(ResourceLocation.withDefaultNamespace("player"))) {
            if (ThreadHelperKt.getPhysLevel(entity) instanceof ClientPhysLevel cl) {
                var partialTicks = cl.getPartialTicks();
                var vb = buffer.getBuffer(RenderType.entityTranslucent(animData.getTextureLocation()));
                var model = CommonModel.get(path);
                var matrix = animatable.getPositionMatrix(partialTicks);
                model.renderBones(animData.getPlayData(), matrix, animatable.getExtraTransform(partialTicks), poseStack.last().normal(), vb, packedLight, LivingEntityRenderer.getOverlayCoords(entity, getWhiteOverlayProgress(entity, partialTicks)), -1, partialTicks);
                ci.cancel();
            }
        }
    }

}
