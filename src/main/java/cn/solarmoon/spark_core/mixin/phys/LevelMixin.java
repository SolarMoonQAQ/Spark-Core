package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevelHolder;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.ServerPhysicsLevel;
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

import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements PhysicsLevelHolder {

    private final Level level = (Level) (Object) this;
    private PhysicsLevel physicsLevel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        if (level.isClientSide) {
            physicsLevel = new ClientPhysicsLevel((ClientLevel)level);
        } else {
            physicsLevel = new ServerPhysicsLevel((ServerLevel) level);
        }
    }

    @Override
    @NotNull
    public PhysicsLevel getPhysicsLevel() {
        return physicsLevel;
    }
}
