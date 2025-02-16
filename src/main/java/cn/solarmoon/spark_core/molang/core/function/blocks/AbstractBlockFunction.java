package cn.solarmoon.spark_core.molang.core.function.blocks;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.level.block.state.BlockBehaviour;

public abstract class AbstractBlockFunction extends ContextFunction<BlockBehaviour> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof BlockBehaviour;
    }
}
