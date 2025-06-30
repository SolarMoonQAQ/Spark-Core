package cn.solarmoon.spark_core.mixin.animation.animatable;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.model.ModelNPCGolem;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelNPCGolem.class)
public abstract class ModelNPCGolemMixin extends EntityModel<EntityNPCInterface> {

    @Inject(
        method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spark_core_injectGolemAnimation(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable) {
            if (animatable.getAnimController().getMainAnim() != null) {
                ci.cancel();
            }
        }
    }
}