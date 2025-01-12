package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin<
        T extends net. minecraft. world. entity. LivingEntity,
        M extends net. minecraft. client. model. HumanoidModel<T>,
        A extends net. minecraft. client. model. HumanoidModel<T>
        > {

    @Inject(method = "getArmorModelHook", at = @At("RETURN"))
    private void setup(T entity, ItemStack itemStack, EquipmentSlot slot, A model, CallbackInfoReturnable<Model> cir) {
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            var model0 = cir.getReturnValue();
            if (model0 instanceof HumanoidModel<?> humanoidModel) {
                VanillaModelHelper.setRoot(humanoidModel.leftArm, humanoidModel.body);
                VanillaModelHelper.setRoot(humanoidModel.rightArm, humanoidModel.body);
                VanillaModelHelper.setRoot(humanoidModel.head, humanoidModel.body);
                VanillaModelHelper.setPivot(animatable, "waist", humanoidModel.body);
            }
        }
    }

}
