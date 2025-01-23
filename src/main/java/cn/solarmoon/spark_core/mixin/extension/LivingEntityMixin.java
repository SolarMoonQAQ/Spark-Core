package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.IAttackedDataPusher;
import cn.solarmoon.spark_core.entity.attack.IExtraDamageDataHolder;
import cn.solarmoon.spark_core.flag.FlagApplier;
import cn.solarmoon.spark_core.flag.FlagHelperKt;
import cn.solarmoon.spark_core.flag.SparkFlags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    private final LivingEntity entity = (LivingEntity) (Object)this;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (entity.level().isClientSide) return;
        var data = ((IAttackedDataPusher)entity).getData();
        if (data != null) {
            ((IExtraDamageDataHolder) source).setData(data);
            ((IAttackedDataPusher)entity).setData(null);
        }
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void doHurtTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        FlagApplier.stopHurtTarget(entity, cir);
    }

}
