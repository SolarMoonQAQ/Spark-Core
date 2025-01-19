package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.AttackedData;
import cn.solarmoon.spark_core.entity.attack.IExtraDamageDataHolder;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DamageSource.class)
public class DamageSourceMixin implements IExtraDamageDataHolder {

    private AttackedData data = null;

    @Override
    public @Nullable AttackedData getData() {
        return data;
    }

    @Override
    public void setData(@Nullable AttackedData attackedData) {
        this.data = attackedData;
    }

}
