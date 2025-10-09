package cn.solarmoon.spark_core.mixin.animation.animatable;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Vindicator.class)
public abstract class VindicatorMixin extends AbstractIllager implements IEntityAnimatable<Vindicator> {

    private Vindicator vindicator = (Vindicator) (Object) this;
    private final AnimController animController = new AnimController(vindicator);
    private final ModelController modelController = new ModelController(vindicator);

    protected VindicatorMixin(EntityType<? extends AbstractIllager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Vindicator getAnimatable() {
        return vindicator;
    }

    @Override
    @NotNull
    public AnimController getAnimController() {
        return animController;
    }

    @Override
    @NotNull
    public ModelController getModelController() {
        return modelController;
    }

    @Override
    public @Nullable Level getAnimLevel() {
        return level();
    }

}
