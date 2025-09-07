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
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Vindicator.class)
public abstract class VindicatorMixin extends AbstractIllager implements IEntityAnimatable<Vindicator> {
    // --- IEntityAnimatable Implementation ---
    private final IKManager ikManager = new IKManager(this);
    private final Map<String, Vec3> ikTargetPositions = new ConcurrentHashMap<>();
    private final Map<String, FabrikChain3D> ikChains = new ConcurrentHashMap<>();

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

    // --- IEntityAnimatable Implementation ---
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
