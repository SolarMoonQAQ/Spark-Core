package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.entity.EntityFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class Position extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int axis = arguments.getAsInt(context, 0);
        float partialTicks = context.entity().getPartialTick();
        Entity entity = context.entity().entity();
        return switch (axis) {
            case 0 -> Mth.lerp(partialTicks, entity.xo, entity.getX());
            case 1 -> Mth.lerp(partialTicks, entity.yo, entity.getY());
            case 2 -> Mth.lerp(partialTicks, entity.zo, entity.getZ());
            default -> null;
        };
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
