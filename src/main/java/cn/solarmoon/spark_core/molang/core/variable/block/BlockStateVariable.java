package cn.solarmoon.spark_core.molang.core.variable.block;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import net.minecraft.world.level.block.state.BlockState;

public class BlockStateVariable extends LambdaVariable<BlockState> {
    public BlockStateVariable(IValueEvaluator<?, IContext<BlockState>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof BlockState;
    }
}
