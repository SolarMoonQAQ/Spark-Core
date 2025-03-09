package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.camera.CameraAdjuster;
import cn.solarmoon.spark_core.camera.CameraAdjusterKt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public class LookControlMixin {

    @Shadow @Final protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick(CallbackInfo ci) {
        if (CameraAdjusterKt.isCameraLocked(mob)) ci.cancel();
    }

}
