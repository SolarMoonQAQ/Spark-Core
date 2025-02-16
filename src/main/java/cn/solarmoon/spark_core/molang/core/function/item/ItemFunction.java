package cn.solarmoon.spark_core.molang.core.function.item;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.item.Item;

public abstract class ItemFunction extends ContextFunction<Item> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Item;
    }
}
