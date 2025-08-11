package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemProperties.class)
public class ItemPropertiesMixin {

    /**
     * 修复原版盾牌在动画中也会在使用时偏移的问题
     */
    @ModifyArgs(
        method = "register(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/item/ClampedItemPropertyFunction;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemProperties;register(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/item/ItemPropertyFunction;)V")
    )
    private static void modifyPropertyArg(Args args) {
        var item = args.get(0);
        var name = args.get(1);
        if (item == Items.SHIELD && name.equals(ResourceLocation.withDefaultNamespace("blocking"))) {
            ClampedItemPropertyFunction property = (stack, level, entity, seed) -> {
                var isPlayingAnim = entity instanceof IEntityAnimatable<?> && ((IEntityAnimatable<?>) entity).getAnimController().isPlayingAnim();
                return (entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !isPlayingAnim) ? 1.0F : 0.0F;
            };
            args.set(2, property);
        }
    }
}
