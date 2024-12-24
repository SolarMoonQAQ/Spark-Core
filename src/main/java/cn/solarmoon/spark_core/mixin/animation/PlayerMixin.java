package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.auto_anim.AutoAnim;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.vanilla.PlayerAnimHelper;
import cn.solarmoon.spark_core.kotlinImpl.IEntityAnimatableJava;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IEntityAnimatableJava<Player> {

    private Player player = (Player) (Object) this;
    private final AnimController<IAnimatable<Player>> animController = new AnimController<>(PlayerAnimHelper.getAnimatable(player));
    private final Set<AutoAnim<?>> autoAnims = new LinkedHashSet<>();

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Player getAnimatable() {
        return player;
    }

    @Override
    public AnimController<IAnimatable<Player>> getAnimController() {
        return animController;
    }

    @Override
    public @NotNull Set<@NotNull AutoAnim<?>> getAutoAnims() {
        return autoAnims;
    }

}
