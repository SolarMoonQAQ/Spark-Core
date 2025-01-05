package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import cn.solarmoon.spark_core.event.EntityGetWeaponEvent;
import cn.solarmoon.spark_core.event.EntityTurnEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder {

    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

}
