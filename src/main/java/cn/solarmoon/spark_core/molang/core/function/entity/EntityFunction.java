package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.world.entity.Entity;

public abstract class EntityFunction extends ContextFunction<Entity> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Entity;
    }
}
