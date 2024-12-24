package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
public class ElytraModelMixin<T extends LivingEntity> {

    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingEntityRenderer<?, ?> lr) {
                var model = lr.getModel();
                if (model instanceof HumanoidModel<? extends LivingEntity> humanoidModel) {
                    VanillaModelHelper.setRoot(leftWing, humanoidModel.body);
                    VanillaModelHelper.setRoot(rightWing, humanoidModel.body);
                }
            }
        }
    }

}
