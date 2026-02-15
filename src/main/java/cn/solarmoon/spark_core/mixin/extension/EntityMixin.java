package cn.solarmoon.spark_core.mixin.extension;

import cn.solarmoon.spark_core.EntityPatch;
import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.entity.attack.HurtData;
import cn.solarmoon.spark_core.gas.*;
import cn.solarmoon.spark_core.gas.sync.*;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.registry.common.SparkSyncerTypes;
import cn.solarmoon.spark_core.state_machine.StateMachineHandler;
import cn.solarmoon.spark_core.sync.IntSyncData;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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
    private final HurtData data = new HurtData();
    private final ConcurrentHashMap<String, PhysicsCollisionObject> collisionObjects = new ConcurrentHashMap<>();
    private final HashMap<ResourceLocation, StateMachineHandler> stateMachineHandlers = new HashMap<>();
    private Vec3 lastPosO = Vec3.ZERO;
    private AbilitySystemComponent asc;
    private final GameplayTagContainer tags = new GameplayTagContainer();

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull StateMachineHandler> getStateMachineHandlers() {
        return stateMachineHandlers;
    }

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return SparkLevel.getPhysicsLevel(level);
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
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
    public @NotNull Vec3 getLastPosO() {
        return lastPosO;
    }

    @Override
    public void setLastPosO(@NotNull Vec3 vec3) {
        lastPosO = vec3;
    }

    @Override
    public @NotNull AbilitySystemComponent getAbilitySystemComponent() {
        return asc;
    }

    @Override
    public void setAbilitySystemComponent(@NotNull AbilitySystemComponent abilitySystemComponent) {
        asc = abilitySystemComponent;
    }

    @Override
    public void syncGiveAbility(@NotNull AbilitySpec<?> spec) {
        PacketDistributor.sendToAllPlayers(new GiveAbilityEntityPayload(id, spec));
    }

    @Override
    public void syncClearAbility(@NotNull AbilityHandle handle) {
        PacketDistributor.sendToAllPlayers(new ClearAbilityEntityPayload(id, handle));
    }

    @Override
    public void syncTryActivateAbility(@NotNull AbilityHandle handle, @NotNull ActivationContext context) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TryActivateAbilityEntityPayload(id, handle, context));
    }

    @Override
    public void syncCancelAbility(@NotNull AbilityHandle handle) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CancelAbilityEntityPayload(id, handle));
    }

    @Override
    public void syncEndAbility(@NotNull AbilityHandle handle) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new EndAbilityEntityPayload(id, handle));
    }

    @Override
    public void syncEndAllAbilities() {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new EndAllAbilitiesEntityPayload(id));
    }

    @Override
    public @NotNull GameplayTagContainer getGameplayTags() {
        return tags;
    }
}
