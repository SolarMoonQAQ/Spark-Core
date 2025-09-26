package cn.solarmoon.spark_core.mixin.animation.animatable;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Zombie.class)
public abstract class ZombieMixin extends Monster implements IEntityAnimatable<Zombie> {

    private Zombie zombie = (Zombie) (Object) this;
    private final AnimController animController = new AnimController(zombie);
    private final ModelController modelController = new ModelController(zombie);

    protected ZombieMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Zombie getAnimatable() {
        return zombie;
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
