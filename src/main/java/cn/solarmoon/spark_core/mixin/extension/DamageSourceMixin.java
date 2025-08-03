package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.IDamageSourceExtraData;
import cn.solarmoon.spark_core.util.BlackBoard;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DamageSource.class)
public class DamageSourceMixin implements IDamageSourceExtraData {

    private final BlackBoard data = new BlackBoard();

    @Override
    public @Nullable BlackBoard getExtraData() {
        return data;
    }

}
