package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.flag.FlagApplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobEntityMixin {

    private final Mob entity = (Mob) (Object)this;

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void doHurt(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        FlagApplier.stopHurtTarget(entity, cir);
    }

}
