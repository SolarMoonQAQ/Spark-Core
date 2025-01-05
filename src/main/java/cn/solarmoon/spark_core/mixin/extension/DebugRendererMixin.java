package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    private Minecraft mc = Minecraft.getInstance();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(Minecraft minecraft, CallbackInfo ci) {
        mc = minecraft;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        var partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        VisualEffectRenderer.getALL_VISUAL_EFFECTS().forEach(i -> i.render(mc, new Vec3(camX, camY, camZ), poseStack, bufferSource, partialTicks));
    }

}
