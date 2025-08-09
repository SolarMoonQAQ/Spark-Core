package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.EntityGetWeaponEvent;
import cn.solarmoon.spark_core.event.PlayerFallEvent;
import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    private Player player = (Player) (Object) this;

    @Inject(method = "getAttackStrengthScale", at = @At("RETURN"), cancellable = true)
    private void getAttackStrengthScale(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        var origin = cir.getReturnValue();
        var event = new PlayerGetAttackStrengthEvent(player, adjustTicks, origin);
        NeoForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getAttackStrengthScale());
    }

    @Inject(method = "getWeaponItem", at = @At("RETURN"), cancellable = true)
    private void getWeapon(CallbackInfoReturnable<ItemStack> cir) {
        var origin = cir.getReturnValue();
        var event = new EntityGetWeaponEvent(player, origin);
        NeoForge.EVENT_BUS.post(event);
        cir.setReturnValue(event.getWeapon());
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void fallOnGround(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        NeoForge.EVENT_BUS.post(new PlayerFallEvent(player, fallDistance, multiplier, source));
    }

}
