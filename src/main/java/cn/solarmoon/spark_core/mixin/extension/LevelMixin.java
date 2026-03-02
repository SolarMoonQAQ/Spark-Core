package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.LevelPatch;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.*;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelPatch {

    private final Level level = (Level) (Object) this;
    private PhysicsLevel physicsLevel;
    private final HashMap<String, PhysicsCollisionObject> collisionObjects = new HashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Function0<Unit>>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Function0<Unit>>> imtasks = new ConcurrentHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        if (level.isClientSide) {
            physicsLevel = new ClientPhysicsLevel((ClientLevel)level, 3); // 客户端60Hz
        } else {
            physicsLevel = new ServerPhysicsLevel((ServerLevel) level, 5); // 服务端100Hz
        }
    }

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
        return physicsLevel;
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
    }

}
