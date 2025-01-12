package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.event.EntityGetWeaponEvent;
import cn.solarmoon.spark_core.event.EntityTurnEvent;
import cn.solarmoon.spark_core.event.StateMachineRegisterEvent;
import cn.solarmoon.spark_core.state_control.IStateMachineHolder;
import cn.solarmoon.spark_core.state_control.StateMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder {

    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

}
