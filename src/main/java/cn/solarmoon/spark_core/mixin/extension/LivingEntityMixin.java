package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.IDamageSourceExtraData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
        var data = entity.getHurtData();
        if (data != null) {
            ((IDamageSourceExtraData)source).setExtraData(data);
            entity.pushHurtData(null);
        }
    }

}
