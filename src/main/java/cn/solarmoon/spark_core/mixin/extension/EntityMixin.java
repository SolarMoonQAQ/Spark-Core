package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.camera.CameraAdjuster;
import cn.solarmoon.spark_core.camera.CameraAdjusterKt;
import cn.solarmoon.spark_core.entity.IEntityPatch;
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.preinput.PreInput;
import cn.solarmoon.spark_core.registry.common.SyncerTypes;
import cn.solarmoon.spark_core.skill.Skill;
import cn.solarmoon.spark_core.skill.SkillHost;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nsk.kstatemachine.statemachine.StateMachine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public class EntityMixin implements IPreInputHolder, HurtDataHolder, SkillHost, Syncer, IEntityPatch {

    @Shadow private int id;
    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private CollisionHurtData data;
    private final ConcurrentHashMap<Integer, Skill> allSkills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Skill> predictedSkills = new ConcurrentHashMap<>();
    private final AtomicInteger skillCount = new AtomicInteger();
    private boolean jumpingLag = false;
    private boolean moving = false;
    private StateMachine stateMachine;

    @Inject(method = "setYRot", at = @At("HEAD"), cancellable = true)
    private void setYRot(float yRot, CallbackInfo ci) {
        if (CameraAdjusterKt.isCameraLocked(entity)) ci.cancel();
    }

    @Inject(method = "setYBodyRot", at = @At("HEAD"), cancellable = true)
    private void setYBodyRot(float yRot, CallbackInfo ci) {
        if (CameraAdjusterKt.isCameraLocked(entity)) ci.cancel();
    }

    @Inject(method = "setYHeadRot", at = @At("HEAD"), cancellable = true)
    private void setYHeadRot(float yRot, CallbackInfo ci) {
        if (CameraAdjusterKt.isCameraLocked(entity)) ci.cancel();
    }

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

    @Override
    public void pushHurtData(CollisionHurtData data) {
        this.data = data;
    }

    @Override
    public CollisionHurtData getHurtData() {
        return data;
    }

    @Override
    public @NotNull SyncerType getSyncerType() {
        return SyncerTypes.getENTITY().get();
    }

    @Override
    public SyncData getSyncData() {
        return new IntSyncData(id);
    }

    @Override
    @NotNull
    public ConcurrentHashMap<Integer, Skill> getAllSkills() {
        return allSkills;
    }

    @Override
    public @NotNull AtomicInteger getSkillCount() {
        return skillCount;
    }

    @Override
    public @NotNull ConcurrentHashMap<@NotNull Integer, @NotNull Skill> getPredictedSkills() {
        return predictedSkills;
    }

    @Override
    public boolean getJumpingLag() {
        return jumpingLag;
    }

    @Override
    public void setJumpingLag(boolean b) {
        jumpingLag = b;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public void setMoving(boolean b) {
        moving = b;
    }

    @Override
    public @Nullable StateMachine getAnimStateMachine() {
        return stateMachine;
    }

    @Override
    public void setAnimStateMachine(@Nullable StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

}
