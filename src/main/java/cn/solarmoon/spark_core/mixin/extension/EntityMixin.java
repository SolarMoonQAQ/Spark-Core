package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.EntityPatch;
import cn.solarmoon.spark_core.entity.IEntityPatch;
import cn.solarmoon.spark_core.entity.attack.HurtData;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.preinput.PreInput;
import cn.solarmoon.spark_core.registry.common.SparkSyncerTypes;
import cn.solarmoon.spark_core.skill.Skill;
import cn.solarmoon.spark_core.skill.SkillHost;
import cn.solarmoon.spark_core.state_machine.StateMachineHandler;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import cn.solarmoon.spark_core.util.InlineEventHandler;
import cn.solarmoon.spark_core.util.InlineEventHandlerKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public class EntityMixin implements EntityPatch {

    @Shadow private int id;
    @Shadow private Level level;
    private Entity entity = (Entity) (Object) this;
    private final PreInput preInput = new PreInput(entity);
    private final HurtData data = new HurtData();
    private final ConcurrentHashMap<Integer, Skill> allSkills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Skill> predictedSkills = new ConcurrentHashMap<>();
    private final AtomicInteger skillCount = new AtomicInteger();
    private final HashMap<String, PhysicsCollisionObject> collisionObjects = new HashMap<>();
    private final HashMap<ResourceLocation, StateMachineHandler> stateMachineHandlers = new HashMap<>();
    private Vec3 lastPosO = Vec3.ZERO;

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull StateMachineHandler> getStateMachineHandlers() {
        return stateMachineHandlers;
    }

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return level.getPhysicsLevel();
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
    }

    @Override
    public @NotNull PreInput getPreInput() {
        return preInput;
    }

    @Override
    public HurtData getHurtData() {
        return data;
    }

    @Override
    public @NotNull SyncerType getSyncerType() {
        return SparkSyncerTypes.getENTITY().get();
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
    public @NotNull Vec3 getLastPosO() {
        return lastPosO;
    }

    @Override
    public void setLastPosO(@NotNull Vec3 vec3) {
        lastPosO = vec3;
    }
}
