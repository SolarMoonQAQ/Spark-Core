package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.KeyboardInputTickEvent;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Shadow @Final private Options options;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tickH(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        var event = NeoForge.EVENT_BUS.post(new KeyboardInputTickEvent.Pre(options, isSneaking, sneakingSpeedMultiplier));
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tickT(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new KeyboardInputTickEvent.Post(options, isSneaking, sneakingSpeedMultiplier));
    }

}
