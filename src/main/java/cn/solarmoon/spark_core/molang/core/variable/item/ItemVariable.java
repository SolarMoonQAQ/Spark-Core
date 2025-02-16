package cn.solarmoon.spark_core.molang.core.variable.item;

import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.item.Item;

public class ItemVariable extends LambdaVariable<Item> {
    public ItemVariable(IValueEvaluator<?, IContext<Item>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Item;
    }
}
