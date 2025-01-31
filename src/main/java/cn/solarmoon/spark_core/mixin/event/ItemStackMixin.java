package cn.solarmoon.spark_core.mixin.event;

import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements DataComponentHolder {

    private final ItemStack stack = (ItemStack) (Object) this;

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void tick(Level level, Entity entity, int inventorySlot, boolean isCurrentItem, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new ItemStackInventoryTickEvent(stack, entity, inventorySlot, isCurrentItem));
    }

}
