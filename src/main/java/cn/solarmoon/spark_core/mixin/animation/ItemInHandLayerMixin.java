package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.api.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.api.phys.thread.ClientPhysLevel;
import cn.solarmoon.spark_core.api.phys.thread.ThreadHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;

    public ItemInHandLayerMixin(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "HEAD"), cancellable = true)
    private void render(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (livingEntity instanceof IEntityAnimatable<?> animatable) {
            var boneName = arm.getSerializedName() + "Item";
            if (!itemStack.isEmpty() && animatable.getAnimData().getModel().hasBone(boneName)) {
                if (ThreadHelperKt.getPhysLevel(livingEntity) instanceof ClientPhysLevel cl) {
                    var partialTicks = cl.getPartialTicks();
                    var p = new PoseStack();
                    var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    p.translate(-cam.x, -cam.y, -cam.z);

                    var ma = animatable.getPositionMatrix(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
                    var bone = animatable.getAnimData().getModel().getBone(boneName);
                    bone.applyTransformWithParents(animatable.getAnimData().getPlayData(), ma, animatable.getExtraTransform(partialTicks), partialTicks);

                    var pivot = animatable.getAnimData().getModel().getBone(boneName).getPivot();
                    p.mulPose(ma);
                    p.translate(pivot.x, pivot.y - 1 / 16f, pivot.z - 1.75 / 16f);
                    p.mulPose(Axis.XP.rotationDegrees(-80.0F));
                    this.itemInHandRenderer.renderItem(livingEntity, itemStack, displayContext, arm == HumanoidArm.LEFT, p, buffer, packedLight);
                    ci.cancel();
                }
            }
        }
    }

}
