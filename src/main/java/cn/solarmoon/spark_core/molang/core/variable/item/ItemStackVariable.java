package cn.solarmoon.spark_core.molang.core.variable.item;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.item.ItemStack;

public class ItemStackVariable extends LambdaVariable<ItemStack> {
    public ItemStackVariable(IValueEvaluator<?, IAnimatable<ItemStack>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IAnimatable<?> context) {
        return context.getAnimatable() instanceof ItemStack;
    }
}
