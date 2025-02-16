package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.core.context.IContext;
import net.minecraft.world.entity.TamableAnimal;

public abstract class TamableEntityFunction extends ContextFunction<TamableAnimal> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof TamableAnimal;
    }
}
