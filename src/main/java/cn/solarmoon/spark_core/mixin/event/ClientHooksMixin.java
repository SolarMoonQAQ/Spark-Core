package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.LocalControllerRegisterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHooks.class)
public class ClientHooksMixin {

    @Inject(method = "initClientHooks", at = @At("TAIL"))
    private static void initClient(Minecraft mc, ReloadableResourceManager resourceManager, CallbackInfo ci) {
        ModLoader.postEvent(new LocalControllerRegisterEvent());
    }

}
