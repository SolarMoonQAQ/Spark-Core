package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.physics.level.*;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.collections.ArrayDeque;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements PhysicsLevelHolder, TaskSubmitOffice {

    private final Level level = (Level) (Object) this;
    private PhysicsLevel physicsLevel;
    private final ConcurrentHashMap<@NotNull String, @NotNull Boolean> pendingKeys = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<@NotNull Pair<@NotNull String, @NotNull Function0<@NotNull Unit>>> tasks = new ConcurrentLinkedDeque<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        if (level.isClientSide) {
            physicsLevel = new ClientPhysicsLevel((ClientLevel)level);
        } else {
            physicsLevel = new ServerPhysicsLevel((ServerLevel) level);
        }
    }

    @Override
    public @NotNull ConcurrentHashMap<@NotNull String, @NotNull Boolean> getPendingKeys() {
        return pendingKeys;
    }

    @Override
    public @NotNull ConcurrentLinkedDeque<@NotNull Pair<@NotNull String, @NotNull Function0<@NotNull Unit>>> getTaskQueue() {
        return tasks;
    }

    @Override
    @NotNull
    public PhysicsLevel getPhysicsLevel() {
        return physicsLevel;
    }
}
