package cn.solarmoon.spark_core.molang.core.variable.block;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.level.block.Block;

public class BlockVariable extends LambdaVariable<Block> {
    public BlockVariable(IValueEvaluator<?, IContext<Block>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Block;
    }
}
