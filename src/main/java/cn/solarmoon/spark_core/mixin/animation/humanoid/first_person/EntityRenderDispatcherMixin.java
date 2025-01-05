package cn.solarmoon.spark_core.mixin.animation.humanoid.first_person;

import cn.solarmoon.spark_core.animation.vanilla.PlayerAnimHelper;
import cn.solarmoon.spark_core.animation.vanilla.PlayerAnimHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void preventRenderShadowWhenInFirstPersonAnim(PoseStack matrices, MultiBufferSource vertexConsumers, Entity entity, float opacity, float tickDelta, LevelReader world, float radius, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player && PlayerAnimHelperKt.shouldRenderArmAnimInFirstPerson(player)) {
            ci.cancel();
        }
    }
}
