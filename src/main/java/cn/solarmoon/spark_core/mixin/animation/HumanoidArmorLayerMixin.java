package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import cn.solarmoon.spark_core.animation.vanilla.player.PlayerAnimHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void cancelHead(PoseStack poseStack, MultiBufferSource bufferSource, T livingEntity, EquipmentSlot slot, int packedLight, A p_model, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (
                slot == EquipmentSlot.HEAD
                        && livingEntity instanceof AbstractClientPlayer player
                        && PlayerAnimHelperKt.shouldRenderArmAnimInFirstPersonEvent(player).getShouldRender()
        ) ci.cancel();
    }
}
