package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.LevelPatch;
import cn.solarmoon.spark_core.mixin_interface.ILevelMixin;
import cn.solarmoon.spark_core.physics.level.*;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelPatch, ILevelMixin {

    private final Level level = (Level) (Object) this;
    private PhysicsLevel physicsLevel;
    private final HashMap<String, PhysicsCollisionObject> collisionObjects = new HashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Function0<Unit>>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Function0<Unit>>> imtasks = new ConcurrentHashMap<>();

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Function0<Unit>>> getTaskMap() {
        return tasks;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Function0<Unit>>> getImmediateQueue() {
        return imtasks;
    }

    @Override
    @NotNull
    public PhysicsLevel getPhysicsLevel() {
        if (physicsLevel == null) {
            throw new IllegalStateException("PhysicsLevel is not initialized for level class: " + level.getClass().getName());
        }
        return physicsLevel;
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
    }

    public void setPhysicsLevel(@NotNull PhysicsLevel physicsLevel) {
        if (this.physicsLevel != null) {
            throw new IllegalStateException("PhysicsLevel has already been initialized for level class: " + level.getClass().getName());
        }
        this.physicsLevel = physicsLevel;
    }

}
