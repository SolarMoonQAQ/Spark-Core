package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.entity.attack.AttackedData;
import cn.solarmoon.spark_core.entity.attack.IAttackedDataPusher;
import cn.solarmoon.spark_core.entity.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.entity.preinput.PreInput;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder, IAttackedDataPusher {

    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private AttackedData data = null;

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

    @Override
    public @Nullable AttackedData getData() {
        return data;
    }

    @Override
    public void setData(@Nullable AttackedData attackedData) {
        data = attackedData;
    }
}
