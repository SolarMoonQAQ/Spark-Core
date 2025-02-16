package cn.solarmoon.spark_core.molang.core.function.entity;

import cn.solarmoon.spark_core.molang.core.context.IContext;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import net.minecraft.client.player.LocalPlayer;

public abstract class LocalPlayerEntityFunction extends ContextFunction<LocalPlayer> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LocalPlayer;
    }
}
