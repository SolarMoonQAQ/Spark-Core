package cn.solarmoon.spark_core.mixin.resource;


import cn.solarmoon.spark_core.mixin_interface.IClientLanguageMixin;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin extends Language implements IClientLanguageMixin {
    @Unique
    public final Map<String, String> spark_core$extraStorage = new HashMap<>();
    @Unique
    public final Map<String, Component> spark_core$componentStorage = new HashMap<>();

    @Override
    public void spark_core$addExtraStorage(Map<String, String> extra) {
        spark_core$extraStorage.putAll(extra);
    }

    @Override
    public void spark_core$addExtraComponentStorage(Map<String, Component> extra) {
        spark_core$componentStorage.putAll(extra);
    }

    @Inject(method = "has",  at = @At("HEAD"), cancellable = true)
    public void has(String key, CallbackInfoReturnable<Boolean> cir) {
        if (spark_core$extraStorage.containsKey(key)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    public void getOrDefault(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
        if (spark_core$extraStorage.containsKey(key)) {
            cir.setReturnValue(spark_core$extraStorage.get(key));
        }
    }

    @Inject(method = "getComponent", at = @At("HEAD"), cancellable = true)
    public void getComponent(String key, CallbackInfoReturnable<Component> cir) {
        if (spark_core$componentStorage.containsKey(key)) {
            cir.setReturnValue(spark_core$componentStorage.get(key));
        }
    }
}
