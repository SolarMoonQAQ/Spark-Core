package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.entity.attack.IDamageSourceExtraData;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DamageSource.class)
public class DamageSourceMixin implements IDamageSourceExtraData {

    private CollisionHurtData data = null;

    @Override
    public @Nullable CollisionHurtData getExtraData() {
        return data;
    }

    @Override
    public void setExtraData(@Nullable CollisionHurtData collisionHurtData) {
        this.data = collisionHurtData;
    }

}
