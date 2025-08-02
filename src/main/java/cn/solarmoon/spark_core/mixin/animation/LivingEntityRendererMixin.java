package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void offset(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.isHumanoidModel(entity)) {
            if (animatable.getModel().hasBone("body")) {
                poseStack.mulPose(animatable.getSpaceBoneMatrix("body", partialTick));
            }
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBedOrientation()Lnet/minecraft/core/Direction;", ordinal = 0)
    )
    private Direction getBedOrientation(LivingEntity instance) {
        return null;
    }

    @Redirect(
            method = "setupRotations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasPose(Lnet/minecraft/world/entity/Pose;)Z", ordinal = 0)
    )
    private boolean sleepRotD(LivingEntity instance, Pose pose) {
        if (pose == Pose.SLEEPING && instance instanceof IAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) return false;
        return instance.hasPose(pose);
    }

    @Redirect(
            method = "setupRotations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasPose(Lnet/minecraft/world/entity/Pose;)Z", ordinal = 1)
    )
    private boolean sleepRot(LivingEntity instance, Pose pose) {
        if (pose == Pose.SLEEPING) return false;
        return instance.hasPose(pose);
    }

}
