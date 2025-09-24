package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;

@Mixin(Entity.class)
public abstract class EntityMixin implements PhysicsHost {

    @Shadow private Level level;

    private final HashMap<String, CollisionObjectComponent<?>> collisionObjects = new HashMap<>();

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return level.getPhysicsLevel();
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull CollisionObjectComponent<?>> getAllCollisionObjects() {
        return collisionObjects;
    }

}
