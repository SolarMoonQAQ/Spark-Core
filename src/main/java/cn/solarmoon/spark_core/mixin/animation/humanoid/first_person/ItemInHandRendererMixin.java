package cn.solarmoon.spark_core.mixin.animation.humanoid.first_person;

import cn.solarmoon.spark_core.animation.vanilla.PlayerAnimHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void disableRenderItemWhenInFirstPersonAnim(float f, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer localPlayer, int i, CallbackInfo ci) {
        if (PlayerAnimHelper.shouldRenderArmAnimInFirstPerson(localPlayer)) {
            ci.cancel();
        }
    }

}
