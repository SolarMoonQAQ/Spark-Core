package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.IAttackedDataPusher;
import cn.solarmoon.spark_core.entity.attack.IExtraDamageDataHolder;
import net.minecraft.world.damagesource.DamageSource;
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
        var data = ((IAttackedDataPusher)entity).getData();
        if (data != null) {
            ((IExtraDamageDataHolder) source).setData(data);
            ((IAttackedDataPusher)entity).setData(null);
        }
    }

}
