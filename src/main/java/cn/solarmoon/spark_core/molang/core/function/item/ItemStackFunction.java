package cn.solarmoon.spark_core.molang.core.function.item;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.item.ItemStack;

public abstract class ItemStackFunction extends ContextFunction<ItemStack> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ItemStack;
    }
}
