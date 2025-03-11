package cn.solarmoon.spark_core.mixin.animation.animatable;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerModel.class)
public abstract class IllagerModelMixin<T extends AbstractIllager> extends HierarchicalModel<T> implements ArmedModel, HeadedModel {

    @Shadow @Final private ModelPart leftArm;

    @Shadow @Final private ModelPart root;

    @Shadow @Final private ModelPart rightArm;

    @Shadow @Final private ModelPart head;

    @Shadow @Final private ModelPart leftLeg;

    @Shadow @Final private ModelPart rightLeg;

    @Shadow @Final private ModelPart arms;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
    private void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        var body = root.getChild("body");
        var nose = head.getChild("nose");
        if (entity instanceof IEntityAnimatable<?> animatable && VanillaModelHelper.shouldSwitchToAnim(animatable)) {
            setDefault();
            if (animatable.getAnimController().getMainAnim() != null) {
                var physPartialTicks = entity.getPhysicsLevel().getPartialTicks();
                var partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                VanillaModelHelper.setRoot(leftArm, body);
                VanillaModelHelper.setRoot(rightArm, body);
                VanillaModelHelper.setRoot(head, body);
                VanillaModelHelper.setPivot(animatable, "waist", body);
                VanillaModelHelper.applyTransform(animatable, "leftArm", leftArm, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "rightArm", rightArm, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "leftLeg", leftLeg, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "rightLeg", rightLeg, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "waist", body, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "head", head, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "nose", nose, partialTicks, physPartialTicks);
                VanillaModelHelper.applyTransform(animatable, "arms", arms, partialTicks, physPartialTicks);
            }
        }
    }

    private void setDefault() {
        leftLeg.setPos(1.9F, 12.0F, 0F);
        rightLeg.setPos(-1.9F, 12.0F, 0F);
        rightArm.setPos(-5f, 2f, 0f);
        leftArm.setPos(5f, 2f, 0f);
        head.setPos(0.0F, 0.0F, 0.0F);
//        body.setPos(0f, 0f, 0f);
        head.setPos(0f, 0f, 0f);

        head.zRot = 0f;

        head.xScale = ModelPart.DEFAULT_SCALE;
        head.yScale = ModelPart.DEFAULT_SCALE;
        head.zScale = ModelPart.DEFAULT_SCALE;
//        body.xScale = ModelPart.DEFAULT_SCALE;
//        body.yScale = ModelPart.DEFAULT_SCALE;
//        body.zScale = ModelPart.DEFAULT_SCALE;
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

        arms.visible = false;
        leftArm.visible = true;
        rightArm.visible = true;
    }

}
