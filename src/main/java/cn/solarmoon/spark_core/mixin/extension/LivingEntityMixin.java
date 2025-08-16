package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.IDamageSourceExtraData;
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
        if (entity.level().isClientSide) return;
        var data = entity.getHurtData();
        if (!data.isEmpty()) {
            ((IDamageSourceExtraData)source).getExtraData().write(data.getStorage());
            // 一旦有数据立刻清空实体的数据缓存，保证每次数据指向的攻击唯一
            data.clear();
        }
    }

}
