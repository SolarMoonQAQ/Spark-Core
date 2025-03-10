package cn.solarmoon.spark_core.mixin.animation.animatable;

import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.anim.state.AnimStateMachineManager;
import cn.solarmoon.spark_core.animation.presets.EntityStateAnimMachine;
import cn.solarmoon.spark_core.animation.presets.PlayerStateAnimMachine;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IEntityAnimatable<Player> {

    @Shadow public abstract boolean isLocalPlayer();

    private final ITempVariableStorage tempStorage = new VariableStorage();
    private final IScopedVariableStorage scopedStorage = new VariableStorage();
    private final IForeignVariableStorage foreignStorage = new VariableStorage();
    private Player player = (Player) (Object) this;
    private final AnimController animController = new AnimController(player);
    private final BoneGroup boneGroup = new BoneGroup(player);

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (isLocalPlayer()) AnimStateMachineManager.INSTANCE.putStateMachine(player, level(), PlayerStateAnimMachine.create((LocalPlayer) player));
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

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return tempStorage;
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return scopedStorage;
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return foreignStorage;
    }

    @Override
    public @Nullable Level getAnimLevel() {
        return level();
    }
}
