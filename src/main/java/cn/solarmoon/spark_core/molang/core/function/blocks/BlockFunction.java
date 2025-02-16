package cn.solarmoon.spark_core.molang.core.function.blocks;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.level.block.Block;

public abstract class BlockFunction extends ContextFunction<Block> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Block;
    }
}
