package cn.solarmoon.spark_core;

import cn.solarmoon.spark_core.entity.IEntityPatch;
import cn.solarmoon.spark_core.entity.attack.HurtDataHolder;
import cn.solarmoon.spark_core.gas.*;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.state_machine.IStateMachineHolder;
import cn.solarmoon.spark_core.state_machine.StateMachineHandler;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.Syncer;
import cn.solarmoon.spark_core.sync.SyncerType;
import cn.solarmoon.spark_core.util.BlackBoard;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface EntityPatch extends PhysicsHost, HurtDataHolder, IStateMachineHolder, IEntityPatch, AbilityHost, Syncer {

    @Override
    default @NotNull GameplayTagContainer getGameplayTags() { return null; };

    @Override
    default void syncGiveAbility(@NotNull AbilitySpec<?> spec) {};

    @Override
    default void syncClearAbility(@NotNull AbilityHandle handle) {};

    @Override
    default void syncTryActivateAbility(@NotNull AbilityHandle handle, @NotNull ActivationContext context) {};

    @Override
    default void syncCancelAbility(@NotNull AbilityHandle handle) {};

    @Override
    default void syncEndAbility(@NotNull AbilityHandle handle) {};

    @Override
    default void syncEndAllAbilities() {};

    @Override
    default void setAbilitySystemComponent(@NotNull AbilitySystemComponent abilitySystemComponent) {};

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
