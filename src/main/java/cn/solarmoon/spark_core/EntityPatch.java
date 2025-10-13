package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.entity.IEntityPatch;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.gas.*;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.preinput.IPreInputHolder;
import cn.solarmoon.spark_core.preinput.PreInput;
import cn.solarmoon.spark_core.skill.Skill;
import cn.solarmoon.spark_core.skill.SkillHost;
import cn.solarmoon.spark_core.state_machine.IStateMachineHolder;
import cn.solarmoon.spark_core.state_machine.StateMachineHandler;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import cn.solarmoon.spark_core.util.BlackBoard;
import cn.solarmoon.spark_core.util.InlineEventHandlerKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface EntityPatch extends PhysicsHost, HurtDataHolder, SkillHost, IPreInputHolder, IStateMachineHolder, IEntityPatch, AbilityHost {

    @Override
    default void syncGrantAbilitySpec(@NotNull AbilitySpec<?> spec) {}

    @Override
    default @NotNull AbilitySystemComponent getAbilitySystemComponent() { return null; }

    @Override
    default @NotNull Vec3 getLastPosO() { return Vec3.ZERO; }

    @Override
    default void setLastPosO(@NotNull Vec3 vec3) {}

    @Override
    default @NotNull BlackBoard getHurtData() {
        return null;
    }

    @Override
    default @NotNull PhysicsLevel getPhysicsLevel() {
        return null;
    }

    @Override
    default @NotNull Map<@NotNull String, @NotNull PhysicsCollisionObject> getAllPhysicsBodies() {
        return Map.of();
    }

    @Override
    default @NotNull AtomicInteger getSkillCount() {
        return null;
    }

    @Override
    default @NotNull ConcurrentHashMap<@NotNull Integer, @NotNull Skill> getAllSkills() {
        return null;
    }

    @Override
    default @NotNull ConcurrentHashMap<@NotNull Integer, @NotNull Skill> getPredictedSkills() {
        return null;
    }

    @Override
    default @NotNull PreInput getPreInput() {
        return null;
    }

    @Override
    default @NotNull Map<@NotNull ResourceLocation, @NotNull StateMachineHandler> getStateMachineHandlers() {
        return Map.of();
    }

    @Override
    default @NotNull SyncerType getSyncerType() {
        return null;
    }

    @Override
    default @NotNull SyncData getSyncData() {
        return null;
    }

}
