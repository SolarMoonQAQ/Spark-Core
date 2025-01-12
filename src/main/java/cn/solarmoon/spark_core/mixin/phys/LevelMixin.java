package cn.solarmoon.spark_core.mixin.phys;

import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent;
import cn.solarmoon.spark_core.phys.IPhysLevelHolder;
import cn.solarmoon.spark_core.phys.PhysWorld;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements IPhysLevelHolder {

    private LinkedHashMap<ResourceLocation, PhysLevel> levelMap = new LinkedHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PhysLevelRegisterEvent((Level) (Object)this, levelMap));
    }

    @Override
    public @NotNull LinkedHashMap<@NotNull ResourceLocation, @NotNull PhysLevel> getAllPhysLevel() {
        return levelMap;
    }

}
