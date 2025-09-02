package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.ClientSparkJS;
import cn.solarmoon.spark_core.js.ISparkJSHolder;
import cn.solarmoon.spark_core.js.ServerSparkJS;
import cn.solarmoon.spark_core.js.SparkJS;
import cn.solarmoon.spark_core.js2.extension.JSLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements ISparkJSHolder, JSLevel  {

    private SparkJS jsEngine;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        if (isClientSide) {
            jsEngine = new ClientSparkJS();
        } else {
            jsEngine = new ServerSparkJS();
        }
    }

    @Override
    public @NotNull SparkJS getJsEngine() {
        return jsEngine;
    }
}
