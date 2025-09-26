package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
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

    private final HashMap<String, PhysicsCollisionObject> collisionObjects = new HashMap<>();

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return level.getPhysicsLevel();
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
    }

}
