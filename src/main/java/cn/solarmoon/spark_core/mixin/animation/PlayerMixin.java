package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData;
import cn.solarmoon.spark_core.animation.vanilla.PlayerAnimHelperKt;
import cn.solarmoon.spark_core.event.BoneUpdateEvent;
import cn.solarmoon.spark_core.phys.SparkMathKt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IEntityAnimatable<Player> {

    private Player player = (Player) (Object) this;
    private final AnimController animController = new AnimController(PlayerAnimHelperKt.asAnimatable(player));
    private final BoneGroup boneGroup = new BoneGroup(PlayerAnimHelperKt.asAnimatable(player));

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public Player getAnimatable() {
        return player;
    }

    @Override
    @NotNull
    public AnimController getAnimController() {
        return animController;
    }

    @Override
    public @NotNull BoneGroup getBones() {
        return boneGroup;
    }

    @Override
    public void onBoneUpdate(@NotNull BoneUpdateEvent event) {
        switch (event.getBone().getName()) {
            case "head" -> {
                var old = event.getNewData();
                event.setNewData(new KeyAnimData(
                        old.getPosition(),
                        SparkMathKt.toRadians(new Vec3(-getXRot(), -yHeadRot + yBodyRot, 0.0).add(old.getRotation())),
                        old.getScale()
                ));
            }
        }
    }

}
