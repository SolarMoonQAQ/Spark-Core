package cn.solarmoon.spark_core.molang.core.variable.item;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.item.ItemStack;

public class ItemStackVariable extends LambdaVariable<ItemStack> {
    public ItemStackVariable(IValueEvaluator<?, IContext<ItemStack>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ItemStack;
    }
}
