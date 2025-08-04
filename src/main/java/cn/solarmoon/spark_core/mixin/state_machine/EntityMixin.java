package cn.solarmoon.spark_core.mixin.state_machine;

import cn.solarmoon.spark_core.state_machine.IStateMachineHolder;
import cn.solarmoon.spark_core.state_machine.StateMachineHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;

@Mixin(Entity.class)
public class EntityMixin implements IStateMachineHolder {

    private final HashMap<ResourceLocation, StateMachineHandler> stateMachineHandlers = new HashMap<>();

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull StateMachineHandler> getStateMachineHandlers() {
        return stateMachineHandlers;
    }

}
