package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.core.context.IContext;
import net.minecraft.world.entity.projectile.AbstractArrow;

public abstract class AbstractArrowEntityFunction extends ContextFunction<AbstractArrow> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractArrow;
    }
}
