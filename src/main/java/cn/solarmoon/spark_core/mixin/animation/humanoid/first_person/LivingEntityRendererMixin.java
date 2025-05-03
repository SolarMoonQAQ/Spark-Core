package cn.solarmoon.spark_core.mixin.animation.humanoid.first_person;

import cn.solarmoon.spark_core.animation.vanilla.player.PlayerAnimHelperKt;
import cn.solarmoon.spark_core.compat.player_animator.PlayerAnimatorCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow @Final protected List<RenderLayer<T, M>> layers;

//    @Redirect(
//            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
//            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;layers:Ljava/util/List;", opcode = Opcodes.GETFIELD)
//    )
//    private List<RenderLayer<T, M>> preventRenderArmorLayerInFirstPersonAnim(LivingEntityRenderer instance, LivingEntity entity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
//        if (entity instanceof AbstractClientPlayer player && PlayerAnimHelperKt.shouldRenderArmAnimInFirstPersonEvent(player).getShouldRender()) {
//            return layers.stream().filter(layer -> layer instanceof PlayerItemInHandLayer<?,?>).toList();
//        } else return layers;
//    }

    private List<RenderLayer<T, M>> layersCache = new ArrayList<>();
    private List<RenderLayer<T, M>> filteredLayers; // 缓存过滤后的列表
    private boolean lastShouldRender; // 上次条件状态

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void h(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player && !PlayerAnimatorCompat.INSTANCE.isLoaded()) {
            boolean shouldRender = PlayerAnimHelperKt.shouldRenderArmAnimInFirstPersonEvent(player).getShouldRender();
            if (shouldRender != lastShouldRender) {
                // 仅在条件变化时重新过滤
                filteredLayers = layers.stream()
                        .filter(layer -> layer instanceof PlayerItemInHandLayer<?,?>)
                        .toList();
                lastShouldRender = shouldRender;
            }

            if (shouldRender) {
                layersCache = new ArrayList<>(layers); // 浅拷贝原始列表
                layers.clear();
                layers.addAll(filteredLayers);
            }
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void p(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (lastShouldRender && !layersCache.isEmpty()) {
            layers.clear();
            layers.addAll(layersCache);
            layersCache.clear();
            lastShouldRender = false;
        }
    }

}
