package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.SparkCore;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.ConcurrentHashMap;


@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {

    private static final ConcurrentHashMap<String, Boolean> SPARK_CORE__ONCE = new ConcurrentHashMap<>();


    @Inject(method = "registerIdMapping", at = @At("HEAD"), cancellable = true, require = 0)
    private void spark_core$guardNegativeIdKey(ResourceKey<T> key, int id, CallbackInfo ci) {
        if (id < 0) {
            try {
                Registry<?> self = (Registry<?>) (Object) this;
                String onceKey = self.key().location() + "|" + key;
                if (SPARK_CORE__ONCE.putIfAbsent(onceKey, Boolean.TRUE) == null) {
                    SparkCore.LOGGER.warn(
                        "Skipping invalid id mapping id={} for {} in registry {}",
                        id, key, self.key().location()
                    );
                }
            } catch (Throwable t) {
                String onceKey = "unknown|" + key;
                if (SPARK_CORE__ONCE.putIfAbsent(onceKey, Boolean.TRUE) == null) {
                    SparkCore.LOGGER.warn("Skipping invalid id mapping id={} for {} (registry unknown)", id, key);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "registerIdMapping(Lnet/minecraft/resources/ResourceKey;I)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spark_core$guardNegativeIdKeyDesc(ResourceKey<T> key, int id, CallbackInfo ci) {
        spark_core$guardNegativeIdKey(key, id, ci);
    }

}
