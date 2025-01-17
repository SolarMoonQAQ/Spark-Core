package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder {

    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

}
