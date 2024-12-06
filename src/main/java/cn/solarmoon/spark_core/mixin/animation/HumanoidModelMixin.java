package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.api.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.api.animation.vanilla.ITransformModel;
import cn.solarmoon.spark_core.api.animation.vanilla.VanillaModelHelper;
import cn.solarmoon.spark_core.api.phys.thread.ClientPhysLevel;
import cn.solarmoon.spark_core.api.phys.thread.ThreadHelperKt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin<T extends LivingEntity> implements ITransformModel {

    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart body;

    boolean transform = true;

    @Inject(method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Ljava/util/function/Function;)V", at = @At("RETURN"))
    private void init(ModelPart root, Function renderType, CallbackInfo ci) {

    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V", ordinal = 0))
    private void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            setDefault();
            if (shouldTransform()) {
                var animData = animatable.getAnimData();
                if (ThreadHelperKt.getPhysLevel(entity) instanceof ClientPhysLevel cl) {
                    var partialTicks = cl.getPartialTicks();
                    VanillaModelHelper.setRoot(leftArm, body);
                    VanillaModelHelper.setRoot(rightArm, body);
                    VanillaModelHelper.setRoot(head, body);
                    VanillaModelHelper.setPivot(animatable.getAnimData(), "waist", body);
                    VanillaModelHelper.applyTransform(animData, "leftArm", leftArm, partialTicks);
                    VanillaModelHelper.applyTransform(animData, "rightArm", rightArm, partialTicks);
                    VanillaModelHelper.applyTransform(animData, "leftLeg", leftLeg, partialTicks);
                    VanillaModelHelper.applyTransform(animData, "rightLeg", rightLeg, partialTicks);
                    VanillaModelHelper.applyTransform(animData, "waist", body, partialTicks);
                    VanillaModelHelper.applyTransform(animData, "head", head, partialTicks);
                }
            } else {
                setShouldTransform(true);
            }
        }
    }

    private void setDefault() {
        leftLeg.setPos(1.9F, 12.0F, 0F);
        rightLeg.setPos(-1.9F, 12.0F, 0F);
        rightArm.setPos(-5f, 2f, 0f);
        leftArm.setPos(5f, 2f, 0f);
        head.setPos(0.0F, 0.0F, 0.0F);
        body.setPos(0f, 0f, 0f);
        head.setPos(0f, 0f, 0f);

        head.zRot = 0f;

        head.xScale = ModelPart.DEFAULT_SCALE;
        head.yScale = ModelPart.DEFAULT_SCALE;
        head.zScale = ModelPart.DEFAULT_SCALE;
        body.xScale = ModelPart.DEFAULT_SCALE;
        body.yScale = ModelPart.DEFAULT_SCALE;
        body.zScale = ModelPart.DEFAULT_SCALE;
        rightArm.xScale = ModelPart.DEFAULT_SCALE;
        rightArm.yScale = ModelPart.DEFAULT_SCALE;
        rightArm.zScale = ModelPart.DEFAULT_SCALE;
        leftArm.xScale = ModelPart.DEFAULT_SCALE;
        leftArm.yScale = ModelPart.DEFAULT_SCALE;
        leftArm.zScale = ModelPart.DEFAULT_SCALE;
        rightLeg.xScale = ModelPart.DEFAULT_SCALE;
        rightLeg.yScale = ModelPart.DEFAULT_SCALE;
        rightLeg.zScale = ModelPart.DEFAULT_SCALE;
        leftLeg.xScale = ModelPart.DEFAULT_SCALE;
        leftLeg.yScale = ModelPart.DEFAULT_SCALE;
        leftLeg.zScale = ModelPart.DEFAULT_SCALE;
    }

    @Override
    public boolean shouldTransform() {
        return transform;
    }

    @Override
    public void setShouldTransform(boolean transform) {
        this.transform = transform;
    }

}
