package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public class EntityMixin implements PhysicsHost {

    @Shadow private Level level;

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return level.getPhysicsLevel();
    }

}
