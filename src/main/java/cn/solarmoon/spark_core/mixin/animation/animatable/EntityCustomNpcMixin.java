package cn.solarmoon.spark_core.mixin.animation.animatable;

import au.edu.federation.caliko.FabrikChain3D;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.ik.component.IKManager;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCFlying;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = EntityCustomNpc.class)
public abstract class EntityCustomNpcMixin extends EntityNPCFlying implements IEntityAnimatable<EntityCustomNpc> {

    private final EntityCustomNpc npc = (EntityCustomNpc) (Object) this;
    private final AnimController animController = new AnimController(this);
    private final BoneGroup boneGroup = new BoneGroup(this);
    private final IKManager ikManager = new IKManager(this); // 'this' refers to IEntityAnimatable
    private final Map<String, Vec3> ikTargetPositions = new ConcurrentHashMap<>();
    private final Map<String, FabrikChain3D> ikChains = new ConcurrentHashMap<>();
    private final ITempVariableStorage tempStorage = new VariableStorage();
    private final IScopedVariableStorage scopedStorage = new VariableStorage();
    private final IForeignVariableStorage foreignStorage = new VariableStorage();

    protected EntityCustomNpcMixin(EntityType<? extends EntityNPCFlying> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public EntityCustomNpc getAnimatable() {
        return this.npc;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return this.animController;
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return this.boneGroup;
    }

    @NotNull
    @Override
    public IKManager getIkManager() {
        return this.ikManager;
    }

    @NotNull
    @Override
    public Map<String, Vec3> getIkTargetPositions() {
        return this.ikTargetPositions;
    }

    @NotNull
    @Override
    public Map<String, FabrikChain3D> getIkChains() {
        return this.ikChains;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return this.tempStorage;
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return this.scopedStorage;
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return this.foreignStorage;
    }

    @Nullable
    @Override
    public Level getAnimLevel() {
        return this.level();
    }
}
