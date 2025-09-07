package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.entity.EntityFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class PositionDelta extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IAnimatable<Entity>> context, ArgumentCollection arguments) {
        int axis = arguments.getAsInt(context, 0);
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Entity entity = context.entity().getAnimatable();
        switch (axis) {
            case 0: return Mth.lerp(partialTicks, entity.xo, entity.getX()) - entity.xo;
            case 1: return Mth.lerp(partialTicks, entity.yo, entity.getY()) - entity.yo;
            case 2: return Mth.lerp(partialTicks, entity.zo, entity.getZ()) - entity.zo;
            default: return null;
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
