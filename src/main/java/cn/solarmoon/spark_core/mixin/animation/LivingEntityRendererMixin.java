package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void offset(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.isHumanoidModel(entity)) {
            var physPartialTicks = ((ClientPhysLevel) ThreadHelperKt.getPhysLevel(entity.level())).getPartialTicks();
            poseStack.mulPose(animatable.getSpaceBoneMatrix("body", partialTick, physPartialTicks));
        }
    }

}
