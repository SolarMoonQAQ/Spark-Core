package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.vanilla.ITransformModelPart;
import cn.solarmoon.spark_core.animation.vanilla.VanillaModelHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(PlayerModel.class)
public class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart jacket;
    @Shadow @Final private ModelPart cloak;

    public PlayerModelMixin(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
        super(root, renderType);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(ModelPart root, boolean slim, CallbackInfo ci) {
        VanillaModelHelper.setRoot(leftSleeve, body);
        VanillaModelHelper.setRoot(rightSleeve, body);
        VanillaModelHelper.setRoot(hat, body);
        VanillaModelHelper.setRoot(cloak, body);
        ((ITransformModelPart)jacket).setPivot(new Vector3f(0f, 12f/16, 0f));
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void setup(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // 暂时没想到怎么更温和的改披风的枢轴变换，直接把layerRenderer里的复制过来了
        if (entity instanceof AbstractClientPlayer livingEntity) {
            if (!livingEntity.isInvisible() && livingEntity.isModelPartShown(PlayerModelPart.CAPE)) {
                PlayerSkin playerskin = livingEntity.getSkin();
                if (playerskin.capeTexture() != null) {
                    ItemStack itemstack = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
                    if (!itemstack.is(Items.ELYTRA)) {
                        var partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                        double d0 = Mth.lerp(partialTicks, livingEntity.xCloakO, livingEntity.xCloak) - Mth.lerp(partialTicks, livingEntity.xo, livingEntity.getX());
                        double d1 = Mth.lerp(partialTicks, livingEntity.yCloakO, livingEntity.yCloak) - Mth.lerp(partialTicks, livingEntity.yo, livingEntity.getY());
                        double d2 = Mth.lerp(partialTicks, livingEntity.zCloakO, livingEntity.zCloak) - Mth.lerp(partialTicks, livingEntity.zo, livingEntity.getZ());
                        float f = Mth.rotLerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
                        double d3 = Mth.sin(f * (float) (Math.PI / 180.0));
                        double d4 = -Mth.cos(f * (float) (Math.PI / 180.0));
                        float f1 = (float) d1 * 10.0F;
                        f1 = Mth.clamp(f1, -6.0F, 32.0F);
                        float f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
                        f2 = Mth.clamp(f2, 0.0F, 150.0F);
                        float f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;
                        f3 = Mth.clamp(f3, -20.0F, 20.0F);
                        if (f2 < 0.0F) {
                            f2 = 0.0F;
                        }

                        float f4 = Mth.lerp(partialTicks, livingEntity.oBob, livingEntity.bob);
                        f1 += Mth.sin(Mth.lerp(partialTicks, livingEntity.walkDistO, livingEntity.walkDist) * 6.0F) * 32.0F * f4;
                        if (livingEntity.isCrouching()) {
                            f1 += 25.0F;
                        }

                        cloak.z = 2f;
                        cloak.xRot = (float) -Math.toRadians(6.0F + f2 / 2.0F + f1);
                        cloak.yRot = (float) Math.toRadians(180.0F - f3 / 2.0F);
                        cloak.zRot = (float) Math.toRadians(f3 / 2.0F);
                    }
                }
            }
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "HEAD"))
    private void setDefault(T livingEntity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        cloak.x = 0;
    }

}
