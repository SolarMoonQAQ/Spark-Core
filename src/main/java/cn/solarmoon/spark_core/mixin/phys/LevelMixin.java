package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.phys.IPhysWorldHolder;
import cn.solarmoon.spark_core.phys.PhysWorld;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelMixin implements IPhysWorldHolder {

    private final PhysWorld physWorld = new PhysWorld(50);

    @Override
    public @NotNull PhysWorld getPhysWorld() {
        return physWorld;
    }

}
