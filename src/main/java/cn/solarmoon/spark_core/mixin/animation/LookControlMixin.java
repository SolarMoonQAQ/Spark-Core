package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public abstract class LookControlMixin {

    @Shadow @Final protected Mob mob;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void sparkCore_onTick(CallbackInfo ci) {
        if (this.mob instanceof IEntityAnimatable<?> animatable) {
            boolean currentAnim = animatable.getAnimController().getAnimLayers().values().stream().anyMatch(animationLayer -> animationLayer.getAnimation() != null && animationLayer.getAnimation().getShouldTurnHead());
            if (!currentAnim) {
                ci.cancel();
            }
        }
    }
}
