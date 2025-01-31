package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.flag.FlagApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
public class MeleeAttackGoalMixin {

    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = "canPerformAttack", at = @At("HEAD"), cancellable = true)
    private void check(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        FlagApplier.stopHurtTarget(mob, cir);
    }

}
