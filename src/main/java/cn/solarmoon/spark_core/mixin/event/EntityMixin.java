package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.EntityGetWeaponEvent;
import cn.solarmoon.spark_core.event.EntityTurnEvent;
import cn.solarmoon.spark_core.event.StateMachineRegisterEvent;
import cn.solarmoon.spark_core.state_control.IStateMachineHolder;
import cn.solarmoon.spark_core.state_control.StateMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;

@Mixin(Entity.class)
public class EntityMixin implements IStateMachineHolder<Entity> {

    private Entity entity = (Entity) (Object) this;
    private final LinkedHashMap<ResourceLocation, StateMachine<Entity>> sm = new LinkedHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityType entityType, Level level, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new StateMachineRegisterEvent.Entity(sm, entity));
    }

    @Override
    public @NotNull LinkedHashMap<@NotNull ResourceLocation, @NotNull StateMachine<Entity>> getAllStateMachines() {
        return sm;
    }

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
