package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.EntityGetWeaponEvent;
import cn.solarmoon.spark_core.event.EntityTurnEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    private Entity entity = (Entity) (Object) this;

    @Inject(method = "getWeaponItem", at = @At("RETURN"), cancellable = true)
    private void getWeapon(CallbackInfoReturnable<ItemStack> cir) {
        var origin = cir.getReturnValue();
        var event = new EntityGetWeaponEvent(entity, origin);
        NeoForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getWeapon());
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void turn(double yRot, double xRot, CallbackInfo ci) {
        var event = new EntityTurnEvent((Entity) (Object)this, xRot, yRot);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) ci.cancel();
    }

}
