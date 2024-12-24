package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import cn.solarmoon.spark_core.phys.thread.ClientPhysLevel;
import cn.solarmoon.spark_core.registry.common.SparkAttachments;
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
            var partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            var pos = animatable.getAnimData().getPlayData().getMixedBoneAnimPosition("root", partialTicks);
            var rot = animatable.getAnimData().getPlayData().getMixedBoneAnimRotation("body", partialTicks);
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.translate(0, 1, 0);
            poseStack.mulPose(new Matrix4f().rotateZYX(rot.z, rot.y, rot.x));
            poseStack.translate(0, -1, 0);
        }
    }

}
