package cn.solarmoon.spark_core.mixin.animation.animatable;

import au.edu.federation.caliko.FabrikChain3D;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.ik.component.IKManager;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = EntityNPCInterface.class, priority = 990)
public abstract class CustomNpcInterfaceMixin extends PathfinderMob implements IEntityAnimatable<EntityNPCInterface> {

    private final EntityNPCInterface npcInterface = (EntityNPCInterface) (Object) this;
    private final AnimController sparkController = new AnimController(this);
    private final BoneGroup sparkBoneGroup = new BoneGroup(this);
    private final IKManager sparkIkManager = new IKManager(this); // 'this' refers to IEntityAnimatable
    private final Map<String, Vec3> sparkIkTargetPositions = new ConcurrentHashMap<>();
    private final Map<String, FabrikChain3D> sparkIkChains = new ConcurrentHashMap<>();
    private final ITempVariableStorage sparkTempStorage = new VariableStorage();
    private final IScopedVariableStorage sparkScopedStorage = new VariableStorage();
    private final IForeignVariableStorage sparkForeignStorage = new VariableStorage();

    protected CustomNpcInterfaceMixin(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // Constructor injection can remain for any other setup or to ensure base class is fully constructed.
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void sparkCore$constructorInit(EntityType<?> entityType, Level level, CallbackInfo ci) {
        // Fields are now initialized directly in their declarations as per ZombieMixin pattern.
        // This injection point can be used for other post-construction logic if needed.
    }

    @Override
    public EntityNPCInterface getAnimatable() {
        return this.npcInterface;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return this.sparkController;
    }

    @NotNull
    @Override
    public BoneGroup getBones() {
        return this.sparkBoneGroup;
    }

    @NotNull
    @Override
    public IKManager getIkManager() {
        return this.sparkIkManager;
    }

    @NotNull
    @Override
    public Map<String, Vec3> getIkTargetPositions() {
        return this.sparkIkTargetPositions;
    }

    @NotNull
    @Override
    public Map<String, FabrikChain3D> getIkChains() {
        return this.sparkIkChains;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return this.sparkTempStorage;
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return this.sparkScopedStorage;
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return this.sparkForeignStorage;
    }

    @Nullable
    @Override
    public Level getAnimLevel() {
        return this.level();
    }
}
