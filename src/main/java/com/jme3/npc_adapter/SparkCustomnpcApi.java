package com.jme3.npc_adapter;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation;
import cn.solarmoon.spark_core.animation.anim.state.AnimStateMachineManager;
import cn.solarmoon.spark_core.animation.sync.AnimShouldTurnPayload;
import cn.solarmoon.spark_core.physics.collision.CollisionCallback;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.presets.callback.CustomnpcCollisionCallback;
import cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsWorld;
import cn.solarmoon.spark_core.js.extension.JSPhysicsHelper;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.math.Vector3f;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker;

import java.util.*;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import cn.solarmoon.spark_core.physics.sync.AddCollisionCallbackPayload;

/**
 * Spark Core Native API for NPC Adapters.
 * Provides functionalities like animation control, model swapping, and collision event handling,
 * independent of WebSocket and JS-bridge implementations.
 */
public class SparkCustomnpcApi {

    // Stores collision event flags. Key: jsFlagVariable, Value: true if collision occurred
    private final Map<String, Boolean> collisionEventFlags = new HashMap<>();

    public SparkCustomnpcApi() {
        // Initialization logic, if any (e.g., logging)
        // SparkCore.LOGGER.info("SparkCustomnpcApi (Native NPC Adapter) initialized.");
    }

    public void shouldTurnBody(String modelId, boolean shouldTurnBody, boolean shouldTurnHead) {
        IAnimatable<?> entity = findAnimatable(modelId);
        if (entity != null) {
            Objects.requireNonNull(entity.getAnimController().getMainAnim()).setShouldTurnBody(shouldTurnBody);
            Objects.requireNonNull(entity.getAnimController().getMainAnim()).setShouldTurnHead(shouldTurnHead);
            PacketDistributor.sendToAllPlayers(new AnimShouldTurnPayload(entity, shouldTurnBody, shouldTurnHead));
        }
    }

    // Placeholder - needs actual implementation
    private IEntityAnimatable<?> findAnimatable(String modelId) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer(); // Use NeoForge API
        if (server == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.findAnimatable: MinecraftServer instance not available via ServerLifecycleHooks.");
            return null;
        }

        ServerLevel level = server.overworld(); // Get Overworld as default

        // 1. Try parsing modelId as an Integer (Entity ID)
        try {
            int entityIdInt = Integer.parseInt(modelId);
            Entity entity = level.getEntity(entityIdInt);
            if (entity instanceof IEntityAnimatable) {
                SparkCore.LOGGER.debug("SparkCustomnpcApi.findAnimatable: Found animatable by Int ID '{}'.", modelId);
                return (IEntityAnimatable<?>) entity;
            }
        } catch (NumberFormatException e) {
            // Not a valid integer, proceed to next checks
        }

        // 2. Try parsing modelId as a UUID string
        try {
            UUID uuid = UUID.fromString(modelId);
            Entity entity = level.getEntity(uuid);
            if (entity instanceof IEntityAnimatable) {
                SparkCore.LOGGER.debug("SparkCustomnpcApi.findAnimatable: Found animatable by UUID '{}'.", modelId);
                return (IEntityAnimatable<?>) entity;
            }
        } catch (IllegalArgumentException e) {
            // Not a valid UUID, normal, proceed to next checks
        }

        // 3. Try searching by custom name or tag (can be slow, consider if this is common)
        // Iterating all entities is resource-intensive. Prefer direct ID/UUID lookup.
        // If custom names/tags are primary identifiers, a dedicated registry would be more efficient.
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof IEntityAnimatable) {
                // Check custom name
                if (entity.getCustomName() != null && entity.getCustomName().getString().equals(modelId)) {
                    SparkCore.LOGGER.debug("SparkCustomnpcApi.findAnimatable: Found animatable by custom name '{}'.", modelId);
                    return (IEntityAnimatable<?>) entity;
                }
                // Check tags
                if (entity.getTags().contains(modelId)) {
                    SparkCore.LOGGER.debug("SparkCustomnpcApi.findAnimatable: Found animatable by tag '{}'.", modelId);
                    return (IEntityAnimatable<?>) entity;
                }
            }
        }

        SparkCore.LOGGER.warn("SparkCustomnpcApi.findAnimatable: Animatable with modelId '{}' not found in default level (overworld) as Int ID, UUID, custom name, or tag.", modelId);
        return null;
    }


    /**
     * Changes the model for a given animatable entity.
     * The new model, its animations, and texture are determined by newModelPath.
     * Assumes newModelPath is a string like "namespace:path".
     */
    public void changeModel(String modelId, String newModelPath) {
        IEntityAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.changeModel: Animatable with id '{}' not found.", modelId);
            return;
        }
        try {
            ResourceLocation modelResLoc = ResourceLocation.tryParse(newModelPath);
            if (modelResLoc == null) {
                SparkCore.LOGGER.error("SparkCustomnpcApi.changeModel: Invalid newModelPath format '{}'. Expected 'namespace:path'.", newModelPath);
                return;
            }
            String entityName = newModelPath.split(":")[1];
            ResourceLocation textureResLoc = ResourceLocation.fromNamespaceAndPath("spark_core", "textures/entity/" + entityName + ".png");
            SparkCore.LOGGER.info(modelResLoc.getNamespace());
            ModelIndex newIdx = new ModelIndex(modelResLoc, modelResLoc, textureResLoc);

            // This is the core action that should trigger a model update and ModelIndexChangeEvent
            animatable.setModelIndex(newIdx);

            SparkCore.LOGGER.info("SparkCustomnpcApi.changeModel: Successfully requested to change model for '{}' to '{}' (ModelIndex: {}). findAnimatable is still a placeholder.", modelId, newModelPath, newIdx);

        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.changeModel: Error changing model for '{}' to '{}': {}", modelId, newModelPath, e.getMessage(), e);
        }
    }

    // --- Collision Event Handling --- (Task 4 & 5)

    // Helper function to find the PhysicsCollisionObject
    private PhysicsCollisionObject findPhysicsObjectForBox(IEntityAnimatable<?> animatable, String boxName) {
        Entity targetEntity = animatable.getAnimatable();
        PhysicsLevel physicsLevel = targetEntity.level().getPhysicsLevel();
        // physicsLevel.world should be PhysicsWorld, which has pcoList
        PhysicsWorld physicsWorld = physicsLevel.getWorld();

        // Assuming pcoList is obtained from physicsWorld
        Collection<PhysicsCollisionObject> pcoList = physicsWorld.getPcoList();
        for (PhysicsCollisionObject pco : pcoList) { // pcoList is from PhysicsWorld
            Object pcoOwner = pco.getOwner(); // Assumes pco.owner gives the Entity
            if (pcoOwner == targetEntity) {
                for (PhysicsCollisionObjectTicker ticker : pco.tickers) { // Assumes pco.tickers is available
                    if (ticker instanceof MoveWithAnimatedBoneTicker) {
                        if (((MoveWithAnimatedBoneTicker) ticker).getBoneName().equals(boxName)) {
                            return pco; // Found the matching PhysicsCollisionObject
                        }
                    }
                }
            }
        }
        SparkCore.LOGGER.warn("SparkCustomnpcApi.findPhysicsObjectForBox: No PhysicsCollisionObject found for entity {} and boxName '{}'.", targetEntity.getId(), boxName);
        return null;
    }
    /**
     * 创建绑定到骨骼的碰撞盒，并同步到客户端
     *
     * @param modelId 模型ID，可以是实体ID、UUID、自定义名称或标签
     * @param boneName 骨骼名称，碰撞盒将跟随此骨骼运动
     * @param sizeX 碰撞盒X轴尺寸（单位：米）
     * @param sizeY 碰撞盒Y轴尺寸（单位：米）
     * @param sizeZ 碰撞盒Z轴尺寸（单位：米）
     * @param offsetX 相对于骨骼原点的X轴偏移量（单位：米）
     * @param offsetY 相对于骨骼原点的Y轴偏移量（单位：米）
     * @param offsetZ 相对于骨骼原点的Z轴偏移量（单位：米）
     * @return 碰撞盒ID，用于后续引用，如果创建失败则返回null
     */
    public String createCollisionBoxBoundToBone(String modelId, String boneName, String cbName,
                                             float sizeX, float sizeY, float sizeZ,
                                             float offsetX, float offsetY, float offsetZ) {
        IEntityAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.createCollisionBoxBoundToBone: Animatable with id '{}' not found.", modelId);
            return null;
        }

        try {
            // 创建Vec3对象表示size和offset
            Vector3f size = new Vector3f(sizeX/2.0f, sizeY/2.0f, sizeZ/2.0f);
            Vector3f offset = new Vector3f(offsetX, offsetY, offsetZ);
            String collisionBoxId = boneName + "_" + animatable.getAnimatable().getStringUUID();
            // 调用JSPhysicsHelper的方法创建碰撞盒
            PhysicsCollisionObject pco = animatable.getAnimatable().bindBody(
                new PhysicsRigidBody(
                     collisionBoxId,
                    (PhysicsHost)animatable, new BoxCollisionShape(size)
                ),
                ((PhysicsHost) animatable).getPhysicsLevel(),
                true,
                t -> {
                    t.addCollisionCallback(new CustomnpcCollisionCallback(
                            cbName,
                            animatable.getAnimatable(),
                            collisionBoxId
                    ));
                    t.setContactResponse(false);
                    t.setKinematic(true);
                    t.setEnableSleep(false);
                    t.setCollideWithGroups(PhysicsCollisionObject.COLLISION_GROUP_OBJECT | PhysicsCollisionObject.COLLISION_GROUP_BLOCK);
                    t.setGravity(Vector3f.ZERO);
                    t.addPhysicsTicker(new MoveWithAnimatedBoneTicker(boneName, offset));
                    return null;
                }
            );
            // 如果是服务端，同步到客户端
            Vec3 size1 = new Vec3(sizeX, sizeY, sizeZ);
            Vec3 offset1 = new Vec3(offsetX, offsetY, offsetZ);
            if (!animatable.getAnimLevel().isClientSide()) {
                Entity entity = animatable.getAnimatable();
                // 创建同步数据包
                PhysicsCollisionObjectSyncPayload payload =
                    new PhysicsCollisionObjectSyncPayload(
                        entity.getSyncerType(),
                        entity.getSyncData(),
                        PhysicsCollisionObjectSyncPayload.Operation.CREATE,
                        collisionBoxId,
                        boneName,
                        size1,
                        offset1
                    );
                // 发送到所有跟踪该实体的玩家
                PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
                SparkCore.LOGGER.info("SparkCustomnpcApi.createCollisionBoxBoundToBone: Synchronized collision box '{}' to clients", collisionBoxId);
            }

            // 返回碰撞盒的唯一标识符
            return collisionBoxId;
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.createCollisionBoxBoundToBone: Error creating collision box", e);
            return null;
        }
    }

    public void addCollisionCallback(String collisionBoxId, String cbName, String UUID, Boolean autoReset, Integer ticks) {
        IEntityAnimatable<?> iAnimatable = findAnimatable(UUID);
        PhysicsCollisionObject pco = findPhysicsObjectForBox(iAnimatable, collisionBoxId);
        Entity entity = iAnimatable.getAnimatable();
        ticks = ticks * 24;
        if (pco != null) {
            // Add callback locally on the server
            pco.addCollisionCallback(new CustomnpcCollisionCallback(cbName, entity, collisionBoxId, autoReset != null ? autoReset : false, ticks != null ? ticks : 0));
            SparkCore.LOGGER.debug("Server: Added CustomnpcCollisionCallback '{}' to PCO '{}' for entity {}.", cbName, collisionBoxId, entity.getStringUUID());

            // If on server, send packet to clients
            if (!entity.level().isClientSide()) {
                AddCollisionCallbackPayload payload = new AddCollisionCallbackPayload(
                        entity.getSyncerType(),
                        entity.getSyncData(),
                        collisionBoxId,
                        cbName,
                        autoReset != null ? autoReset : false, // Ensure non-null for payload
                        ticks // Can be null for Int? in payload
                );
                PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
                SparkCore.LOGGER.debug("Server: Sent AddCollisionCallbackPayload for PCO '{}', CB '{}' on entity {}.", collisionBoxId, cbName, entity.getStringUUID());
            }
        } else {
            SparkCore.LOGGER.warn("SparkCustomnpcApi.addCollisionCallback: PhysicsCollisionObject with ID '{}' not found.", collisionBoxId);
        }
    }

    public void resetEntityByCbname(String UUID, String cbName, String collisionBoxId){
        IEntityAnimatable<?> iAnimatable = findAnimatable(UUID);
        if (iAnimatable != null) {
            PhysicsCollisionObject pco = findPhysicsObjectForBox(iAnimatable, collisionBoxId);
            if (pco != null) {
                for (CollisionCallback callback : pco.collisionListeners){
                    if (callback instanceof CustomnpcCollisionCallback && ((CustomnpcCollisionCallback) callback).getCbName().equals(cbName)) {
                        ((CustomnpcCollisionCallback) callback).getAttackSystem().reset();
                    }
                }
            }
        }
    }

    private void removeCollisionCallback(String collisionBoxId, String cbName) {
        PhysicsCollisionObject pco = findPhysicsObjectByName(collisionBoxId);
        if (pco != null) {
            pco.collisionListeners.removeIf(callback -> callback instanceof CustomnpcCollisionCallback && ((CustomnpcCollisionCallback) callback).getCbName().equals(cbName));
        }
    }

    /**
     * 根据名称查找PhysicsCollisionObject对象
     *
     *
     * @param name 碰撞对象的名称
     * @return 找到的PhysicsCollisionObject对象，如果未找到则返回null
     */
    private PhysicsCollisionObject findPhysicsObjectByName(String name) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.findPhysicsObjectByName: MinecraftServer instance not available via ServerLifecycleHooks.");
            return null;
        }

        // 遍历所有服务器世界
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            PhysicsLevel physicsLevel = level.getPhysicsLevel();
            PhysicsWorld physicsWorld = physicsLevel.getWorld();

            // 遍历世界中的所有PhysicsCollisionObject
            for (PhysicsCollisionObject pco : physicsWorld.getPcoList()) {
                // 比较名称并返回匹配的对象
                if (pco.name.equals(name)) {
                    return pco;
                }
            }
        }

        // 如果未找到则返回null
        SparkCore.LOGGER.warn("SparkCustomnpcApi.findPhysicsObjectByName: No PhysicsCollisionObject found with name '{}'.", name);
        return null;
    }

    /**
     * 获取碰撞点A的位置
     *
     * @param manifoldId 碰撞点ID
     * @return 碰撞点A的位置，表示为[x, y, z]的double数组
     */
    public double[] getContactPosA(long manifoldId) {
        Vec3 pos = JSPhysicsHelper.INSTANCE.getContactPosA(manifoldId);
        return new double[] { pos.x, pos.y, pos.z };
    }

    /**
     * 获取碰撞点B的位置
     *
     * @param manifoldId 碰撞点ID
     * @return 碰撞点B的位置，表示为[x, y, z]的double数组
     */
    public double[] getContactPosB(long manifoldId) {
        Vec3 pos = JSPhysicsHelper.INSTANCE.getContactPosB(manifoldId);
        return new double[] { pos.x, pos.y, pos.z };
    }
    /**
     * 播放已注册的TypedAnimation
     *
     * @param UUID 实体 UUID
     * @param animationId 动画ID，由registerTypedAnimation方法返回
     * @param transTime 动画持续时间（单位：tick）
     * @return 是否成功播放动画
     */
    public boolean playTypedAnimation(String UUID, String animationId, int transTime) {
        IEntityAnimatable<?> animatable = findAnimatable(UUID);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Animatable with id '{}' not found", UUID);
            return false;
        }

        try {
            // 查找注册的TypedAnimation
            ResourceLocation animationResLoc = ResourceLocation.parse(animationId);
            TypedAnimation animation = SparkRegistries.getTYPED_ANIMATION().get(animationResLoc);
            if (animation == null) {
                SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Animation with id '{}' not found", animationId);
                return false;
            }

            // 播放动画
            animation.play(animatable, transTime, false);

            // 如果是服务端，同步到客户端
            if (!animatable.getAnimLevel().isClientSide()) {
                Entity entity = animatable.getAnimatable();
                animation.syncToClient(entity.getId(), transTime, false, null);
            }

            return true;
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Error playing animation", e);
            return false;
        }
    }


    public void registerEntityAnimationOverride(String UUID, String animationStateKey, String animationId) {
        animationStateKey = "spark_core:" + animationStateKey;
        ResourceLocation animationResLoc = ResourceLocation.parse(animationId);
        TypedAnimation animation = SparkRegistries.getTYPED_ANIMATION().get(animationResLoc);
        if (animation != null) {
            AnimStateMachineManager.INSTANCE.registerEntityAnimationOverride(UUID, animationStateKey, animation);
        }
    }


    /**
     * 播放模型动画
     *
     * @param UUID entity的UUID
     * @param namespace 资源命名空间，默认为"player"
     * @param animationName 动画名称，如 "attack"
     * @param transTime 动画持续时间（单位：tick）
     */
    public Boolean playAnimation(String UUID, String namespace, String animationName, int transTime, float speed, float speedChangeDuration) {
        IEntityAnimatable<?> animatable = findAnimatable(UUID);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Animatable with id '{}' not found", UUID);
            return false;
        }
        // 生成唯一的动画ID，格式为 namespace:animationName
        String animationId = namespace + "/" + animationName;
        try {
            // 查找注册的TypedAnimation
            ResourceLocation animationResLoc = ResourceLocation.withDefaultNamespace(animationId);
            TypedAnimation animation = SparkRegistries.getTYPED_ANIMATION().get(animationResLoc);

            if (animation == null) {
                SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Animation with id '{}' not found", animationId);
                return false;
            }

            // 播放动画
            animation.play(animatable, transTime, false);
            // speed转成int
            int speedInt = (int) (speed * 20);
            animatable.changeSpeed(speedInt, speedChangeDuration);
            // 如果是服务端，同步到客户端
            if (!animatable.getAnimLevel().isClientSide()) {
                Entity entity = animatable.getAnimatable();
                animation.syncToClient(entity.getId(), transTime, false, null);
            }
            return true;
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkCustomnpcApi.playTypedAnimation: Error playing animation", e);
            return false;
        }
    }
}