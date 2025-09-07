package cn.solarmoon.spark_core.mixin.animation.animatable;

import au.edu.federation.caliko.FabrikChain3D;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.model.BonePoseGroup;
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.ik.component.IKManager;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IEntityAnimatable<Player> {

    @Shadow public abstract boolean isLocalPlayer();

    private Player player = (Player) (Object) this;
    private final AnimController animController = new AnimController(player);
    private final ModelController modelController = new ModelController(player);
    // --- IEntityAnimatable Implementation ---private final MutableMap<String, Vec3> ikChains = ConcurrentMap();
    private final IKManager ikManager = new IKManager(this);
    // Use Map interface type, ConcurrentHashMap for potential thread safety (though likely accessed main-thread only)
    private final Map<String, Vec3> ikTargetPositions = new ConcurrentHashMap<>();
    private final Map<String, FabrikChain3D> ikChains = new ConcurrentHashMap<>();

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
    @NotNull
    public ModelController getModelController() {return modelController;}

    @Override
    public @Nullable Level getAnimLevel() {
        return level();
    }

    @NotNull
    @Override
    public IKManager getIkManager() {
        return this.ikManager;
    }

    @NotNull
    @Override
    public Map<String, Vec3> getIkTargetPositions() {
        // Return the map instance. The caller (IKManager) will modify it.
        return this.ikTargetPositions;
    }

    @NotNull
    @Override
    public Map<String, FabrikChain3D> getIkChains() {
        // Return the map instance. The caller (IKManager) will modify it.
        return this.ikChains;
    }

}
