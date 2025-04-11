package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.ClientSparkJS;
import cn.solarmoon.spark_core.js.ISparkJSHolder;
import cn.solarmoon.spark_core.js.ServerSparkJS;
import cn.solarmoon.spark_core.js.SparkJS;
import cn.solarmoon.spark_core.js.extension.JSLevel;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.ServerPhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements ISparkJSHolder, JSLevel {

    private final Level level = (Level) (Object) this;
    private SparkJS js;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(WritableLevelData levelData, ResourceKey dimension, RegistryAccess registryAccess, Holder dimensionTypeRegistration, Supplier profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        if (isClientSide) {
            js = new ClientSparkJS();
        } else {
            js = new ServerSparkJS();
        }
    }

    @Override
    public @NotNull SparkJS getJsEngine() {
        return js;
    }

}
