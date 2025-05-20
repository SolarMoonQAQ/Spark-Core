package com.jme3.npc_adapter;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation;
import cn.solarmoon.spark_core.network.dynamic.NetworkHandler;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.registry.common.SparkRegistries;
import cn.solarmoon.spark_core.registry.common.SparkTypedAnimations;
import kotlin.Unit;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsWorld;
import cn.solarmoon.spark_core.js.extension.JSPhysicsHelper;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.npc_adapter.event.SparkPhysicsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker;
import cn.solarmoon.spark_core.physics.presets.callback.HitReactionCollisionCallback;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;



/**
 * Spark Core Native API for NPC Adapters.
 * Provides functionalities like animation control, model swapping, and collision event handling,
 * independent of WebSocket and JS-bridge implementations.
 */
public class SparkApi {

    // Stores collision event flags. Key: jsFlagVariable, Value: true if collision occurred
    private final Map<String, Boolean> collisionEventFlags = new HashMap<>();

    public SparkApi() {
        // Initialization logic, if any (e.g., logging)
        // SparkCore.LOGGER.info("SparkApi (Native NPC Adapter) initialized.");
    }

    // Placeholder - needs actual implementation
    private IAnimatable<?> findAnimatable(String modelId) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer(); // Use NeoForge API
        if (server == null) {
            SparkCore.LOGGER.error("SparkApi.findAnimatable: MinecraftServer instance not available via ServerLifecycleHooks.");
            return null;
        }

        net.minecraft.server.level.ServerLevel level = server.overworld(); // Get Overworld as default

        // 1. Try parsing modelId as an Integer (Entity ID)
        try {
            int entityIdInt = Integer.parseInt(modelId);
            Entity entity = level.getEntity(entityIdInt);
            if (entity instanceof IAnimatable) {
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by Int ID '{}'.", modelId);
                return (IAnimatable<?>) entity;
            }
        } catch (NumberFormatException e) {
            // Not a valid integer, proceed to next checks
        }

        // 2. Try parsing modelId as a UUID string
        try {
            UUID uuid = UUID.fromString(modelId);
            Entity entity = level.getEntity(uuid);
            if (entity instanceof IAnimatable) {
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by UUID '{}'.", modelId);
                return (IAnimatable<?>) entity;
            }
        } catch (IllegalArgumentException e) {
            // Not a valid UUID, normal, proceed to next checks
        }

        // 3. Try searching by custom name or tag (can be slow, consider if this is common)
        // Iterating all entities is resource-intensive. Prefer direct ID/UUID lookup.
        // If custom names/tags are primary identifiers, a dedicated registry would be more efficient.
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof IAnimatable) {
                // Check custom name
                if (entity.getCustomName() != null && entity.getCustomName().getString().equals(modelId)) {
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by custom name '{}'.", modelId);
                    return (IAnimatable<?>) entity;
                }
                // Check tags
                if (entity.getTags().contains(modelId)) {
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by tag '{}'.", modelId);
                    return (IAnimatable<?>) entity;
                }
            }
        }

        SparkCore.LOGGER.warn("SparkApi.findAnimatable: Animatable with modelId '{}' not found in default level (overworld) as Int ID, UUID, custom name, or tag.", modelId);
        return null;
    }


    /**
     * Changes the model for a given animatable entity.
     * The new model, its animations, and texture are determined by newModelPath.
     * Assumes newModelPath is a string like "namespace:path".
     */
    public void changeModel(String modelId, String newModelPath) {
        IAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Animatable with id '{}' not found.", modelId);
            return;
        }

        try {
            ResourceLocation modelResLoc = ResourceLocation.tryParse(newModelPath);
            if (modelResLoc == null) {
                SparkCore.LOGGER.error("SparkApi.changeModel: Invalid newModelPath format '{}'. Expected 'namespace:path'.", newModelPath);
                return;
            }
            String entityName = newModelPath.split(":")[1];
            ResourceLocation textureResLoc = ResourceLocation.fromNamespaceAndPath("spark_core", "textures/entity/" + entityName + ".png");
            SparkCore.LOGGER.info(modelResLoc.getNamespace());
            ModelIndex newIdx = new ModelIndex(modelResLoc, modelResLoc, textureResLoc);

            // This is the core action that should trigger a model update and ModelIndexChangeEvent
            animatable.setModelIndex(newIdx);

            SparkCore.LOGGER.info("SparkApi.changeModel: Successfully requested to change model for '{}' to '{}' (ModelIndex: {}). findAnimatable is still a placeholder.", modelId, newModelPath, newIdx);

        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Error changing model for '{}' to '{}': {}", modelId, newModelPath, e.getMessage(), e);
        }
    }

    // --- Collision Event Handling --- (Task 4 & 5)

    // Helper function to find the PhysicsCollisionObject
    private PhysicsCollisionObject findPhysicsObjectForBox(IAnimatable<?> animatable, String boxName) {
        Entity targetEntity = (Entity) animatable.getAnimatable();
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: Animatable's underlying object (modelId: '{}') is not an Entity or is null.", animatable.getModelIndex().getModelPath());
            return null;
        }

        PhysicsLevel physicsLevel = targetEntity.level().getPhysicsLevel();
        // physicsLevel.world should be PhysicsWorld, which has pcoList
        Object physicsWorld = physicsLevel.getWorld();

        // Assuming pcoList is obtained from physicsWorld
        java.util.List<PhysicsCollisionObject> pcoList = (java.util.List<PhysicsCollisionObject>) ((PhysicsWorld) physicsWorld).getPcoList();
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
        SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: No PhysicsCollisionObject found for entity {} and boxName '{}'.", targetEntity.getId(), boxName);
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
    public String createCollisionBoxBoundToBone(String modelId, String boneName,
                                             float sizeX, float sizeY, float sizeZ,
                                             float offsetX, float offsetY, float offsetZ) {
        IAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.createCollisionBoxBoundToBone: Animatable with id '{}' not found.", modelId);
            return null;
        }

        try {
            // 检查animatable是否为IEntityAnimatable类型
            if (!(animatable instanceof IEntityAnimatable)) {
                SparkCore.LOGGER.error("SparkApi.createCollisionBoxBoundToBone: Animatable must be an entity type!");
                return null;
            }

            // 创建Vec3对象表示size和offset
            Vec3 size = new Vec3(sizeX, sizeY, sizeZ);
            Vec3 offset = new Vec3(offsetX, offsetY, offsetZ);

            // 调用JSPhysicsHelper的方法创建碰撞盒
            PhysicsCollisionObject pco = JSPhysicsHelper.INSTANCE.createCollisionBoxBoundToBone(
                animatable, boneName, size, offset, null
            );
            String collisionBoxId = pco.name;
            // 如果是服务端，同步到客户端
            if (!animatable.getAnimLevel().isClientSide()) {
                Entity entity = (Entity) ((IEntityAnimatable<?>) animatable).getAnimatable();

                // 创建同步数据包
                cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload payload =
                    new cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload(
                        entity.getSyncerType(),
                        entity.getSyncData(),
                        cn.solarmoon.spark_core.physics.sync.PhysicsCollisionObjectSyncPayload.Operation.CREATE,
                        collisionBoxId,
                        boneName,
                        size,
                        offset
                    );

                // 发送到所有跟踪该实体的玩家
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, payload);

                SparkCore.LOGGER.info("SparkApi.createCollisionBoxBoundToBone: Synchronized collision box '{}' to clients", collisionBoxId);
            }

            // 返回碰撞盒的唯一标识符
            return collisionBoxId;
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.createCollisionBoxBoundToBone: Error creating collision box", e);
            return null;
        }
    }

    /**
     * 根据名称查找PhysicsCollisionObject对象
     *
     * @param name 碰撞对象的名称
     * @return 找到的PhysicsCollisionObject对象，如果未找到则返回null
     */
    private PhysicsCollisionObject findPhysicsObjectByName(String name) {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            SparkCore.LOGGER.error("SparkApi.findPhysicsObjectByName: MinecraftServer instance not available via ServerLifecycleHooks.");
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
        SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectByName: No PhysicsCollisionObject found with name '{}'.", name);
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
     * 为碰撞盒注册碰撞回调
     *
     * @param collisionBoxId 碰撞盒ID，由createCollisionBoxBoundToBone方法返回
     * @param eventId 事件ID，用于在JavaScript中识别事件
     */
    public void registerCollisionCallback(String collisionBoxId, int eventId) {
        // 查找碰撞盒
        PhysicsCollisionObject pco = findPhysicsObjectByName(collisionBoxId);
        if (pco == null) {
            SparkCore.LOGGER.error("SparkApi.registerCollisionCallback: PhysicsCollisionObject with id '{}' not found.", collisionBoxId);
            return;
        }

        try {
            // 添加碰撞回调
            pco.addCollisionCallback(new cn.solarmoon.spark_core.physics.collision.CollisionCallback() {
                @Override
                public void onStarted(@NotNull PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, long manifoldId) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.CollisionStartEvent event =
                        new SparkPhysicsEvent.CollisionStartEvent(o1, o2, manifoldId);
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 触发脚本事件
                    if (o1.getOwner() instanceof Entity entity) {
                        noppes.npcs.EventHooks.onScriptTriggerEvent(
                            eventId,
                            noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) entity.level()),
                            noppes.npcs.api.NpcAPI.Instance().getIPos(entity.getX(), entity.getY(), entity.getZ()),
                            noppes.npcs.api.NpcAPI.Instance().getIEntity(entity),
                            new Object[] { "start", o2.name, manifoldId }
                        );
                    }
                }

                @Override
                public void onProcessed(@NotNull PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, long manifoldId) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.CollisionProcessedEvent event =
                        new SparkPhysicsEvent.CollisionProcessedEvent(o1, o2, manifoldId);
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 触发脚本事件
                    if (o1.getOwner() instanceof Entity entity) {
                        noppes.npcs.EventHooks.onScriptTriggerEvent(
                            eventId,
                            noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) entity.level()),
                            noppes.npcs.api.NpcAPI.Instance().getIPos(entity.getX(), entity.getY(), entity.getZ()),
                            noppes.npcs.api.NpcAPI.Instance().getIEntity(entity),
                            new Object[] { "processed", o2.name, manifoldId }
                        );
                    }
                }

                @Override
                public void onEnded(@NotNull PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, long manifoldId) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.CollisionEndEvent event =
                        new SparkPhysicsEvent.CollisionEndEvent(o1, o2, manifoldId);
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 触发脚本事件
                    if (o1.getOwner() instanceof Entity entity) {
                        noppes.npcs.EventHooks.onScriptTriggerEvent(
                            eventId,
                            noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) entity.level()),
                            noppes.npcs.api.NpcAPI.Instance().getIPos(entity.getX(), entity.getY(), entity.getZ()),
                            noppes.npcs.api.NpcAPI.Instance().getIEntity(entity),
                            new Object[] { "end", o2.name, manifoldId }
                        );
                    }
                }
            });

            SparkCore.LOGGER.info("SparkApi.registerCollisionCallback: Successfully registered collision callback for '{}' with event ID {}",
                collisionBoxId, eventId);
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.registerCollisionCallback: Error registering collision callback", e);
        }
    }

    /**
     * 为碰撞盒注册攻击碰撞回调的便捷方法, 带有攻击和受击动画id
     *
     * @param collisionBoxId 碰撞盒ID，由createCollisionBoxBoundToBone方法返回
     * @param eventId 事件ID，用于在JavaScript中识别事件
     * @param attackAnimationId 攻击时播放的动画ID，由registerTypedAnimation方法返回，可以为null
     * @param hitAnimationId 受击时播放的动画ID，由registerTypedAnimation方法返回，可以为null
     */
    public void registerAttackCollisionCallback(String collisionBoxId, int eventId, String attackAnimationId, String hitAnimationId) {
        // 查找碰撞盒
        PhysicsCollisionObject pco = findPhysicsObjectByName(collisionBoxId);
        if (pco == null) {
            SparkCore.LOGGER.error("SparkApi.registerAttackCollisionCallback: PhysicsCollisionObject with id '{}' not found.", collisionBoxId);
            return;
        }

        try {
            // 添加攻击碰撞回调
            pco.addCollisionCallback(new cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback() {
                @Override
                public cn.solarmoon.spark_core.entity.attack.@NotNull AttackSystem getAttackSystem() {
                    return new cn.solarmoon.spark_core.entity.attack.AttackSystem();
                }

                @Override
                public void preAttack(
                        boolean isFirst,
                        net.minecraft.world.entity.@NotNull Entity attacker,
                        net.minecraft.world.entity.@NotNull Entity target,
                        @NotNull PhysicsCollisionObject aBody,
                        @NotNull PhysicsCollisionObject bBody,
                        long manifoldId
                ) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.AttackCollisionEvent event =
                        new SparkPhysicsEvent.AttackCollisionEvent(
                            attacker, target, aBody, bBody, manifoldId, "pre"
                        );
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 如果指定了攻击动画ID，播放攻击动画
                    if (attackAnimationId != null && !attackAnimationId.isEmpty()) {
                        playTypedAnimation(attacker.getStringUUID(), attackAnimationId, 5);
                    }

                    // 触发脚本事件
                    noppes.npcs.EventHooks.onScriptTriggerEvent(
                        eventId,
                        noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) attacker.level()),
                        noppes.npcs.api.NpcAPI.Instance().getIPos(attacker.getX(), attacker.getY(), attacker.getZ()),
                        noppes.npcs.api.NpcAPI.Instance().getIEntity(attacker),
                        new Object[]{"preAttack", isFirst, target.getId(), manifoldId}
                    );
                }

                @Override
                public boolean doAttack(
                        net.minecraft.world.entity.@NotNull Entity attacker,
                        net.minecraft.world.entity.@NotNull Entity target,
                        @NotNull PhysicsCollisionObject aBody,
                        @NotNull PhysicsCollisionObject bBody,
                        long manifoldId
                ) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.AttackCollisionEvent event =
                        new SparkPhysicsEvent.AttackCollisionEvent(
                            attacker, target, aBody, bBody, manifoldId, "do"
                        );
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 如果指定了受击动画ID，播放受击动画
                    if (hitAnimationId != null && !hitAnimationId.isEmpty()) {
                        playTypedAnimation(target.getStringUUID(), hitAnimationId, 5);
                    }

                    // 触发脚本事件
                    noppes.npcs.EventHooks.onScriptTriggerEvent(
                        eventId,
                        noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) attacker.level()),
                        noppes.npcs.api.NpcAPI.Instance().getIPos(attacker.getX(), attacker.getY(), attacker.getZ()),
                        noppes.npcs.api.NpcAPI.Instance().getIEntity(attacker),
                        new Object[]{"doAttack", target.getId(), manifoldId}
                    );

                    return true; // 允许攻击
                }

                @Override
                public void postAttack(
                        net.minecraft.world.entity.@NotNull Entity attacker,
                        net.minecraft.world.entity.@NotNull Entity target,
                        @NotNull PhysicsCollisionObject aBody,
                        @NotNull PhysicsCollisionObject bBody,
                        long manifoldId
                ) {
                    // 发布事件到CustomNPCs事件总线
                    SparkPhysicsEvent.AttackCollisionEvent event =
                        new SparkPhysicsEvent.AttackCollisionEvent(
                            attacker, target, aBody, bBody, manifoldId, "post"
                        );
                    noppes.npcs.api.NpcAPI.Instance().events().post(event);

                    // 触发脚本事件
                    noppes.npcs.EventHooks.onScriptTriggerEvent(
                        eventId,
                        noppes.npcs.api.NpcAPI.Instance().getIWorld((ServerLevel) attacker.level()),
                        noppes.npcs.api.NpcAPI.Instance().getIPos(attacker.getX(), attacker.getY(), attacker.getZ()),
                        noppes.npcs.api.NpcAPI.Instance().getIEntity(attacker),
                        new Object[]{"postAttack", target.getId(), manifoldId}
                    );
                }
            });

            SparkCore.LOGGER.info("SparkApi.registerAttackCollisionCallback: Successfully registered attack collision callback for '{}' with event ID {}, attack animation ID {} and hit animation ID {}",
                collisionBoxId, eventId, attackAnimationId != null ? attackAnimationId : "none", hitAnimationId != null ? hitAnimationId : "none");
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.registerAttackCollisionCallback: Error registering attack collision callback", e);
        }
    }

    /**
     * 注册TypedAnimation，允许在JavaScript中创建和播放自定义动画
     * 使用动态注册系统，可以在运行时注册和卸载动画
     *
     * @param animationId 动画的唯一标识符
     * @param namespace 资源命名空间，默认为"player"
     * @param path 动画资源路径, 如"EntityState/attack"
     * @return 注册的动画ID，用于后续引用，如果注册失败则返回null
     */
    public String registerTypedAnimation(String animationId, String namespace, String path) {
        // 检查参数
        if (animationId == null || animationId.isEmpty()) {
            SparkCore.LOGGER.error("SparkApi.registerTypedAnimation: Animation ID cannot be empty");
            return null;
        }

        if (path == null || path.isEmpty()) {
            SparkCore.LOGGER.error("SparkApi.registerTypedAnimation: Animation path cannot be empty");
            return null;
        }

        try {
            // 处理可能包含非法字符的动画ID
            // 先检查是否包含冒号，如果包含则可能是已经是完整的ResourceLocation格式
            String safeAnimationId = animationId;
            if (animationId.contains(":")) {
                // 如果包含冒号，取最后一个冒号后的部分作为路径
                int lastColonIndex = animationId.lastIndexOf(':');
                safeAnimationId = animationId.substring(lastColonIndex + 1);

                // 如果还有冒号，则替换为下划线
                safeAnimationId = safeAnimationId.replace(':', '_');

                SparkCore.LOGGER.warn("SparkApi.registerTypedAnimation: Animation ID '{}' contains colon, using '{}' as path",
                        animationId, safeAnimationId);
            }

            // 创建完整的ResourceLocation用于动态注册
            ResourceLocation fullId = ResourceLocation.fromNamespaceAndPath(namespace, safeAnimationId);

            // 创建模型的ResourceLocation
            ResourceLocation modelRL = ResourceLocation.withDefaultNamespace(namespace);

            // 创建 AnimIndex
            AnimIndex animIndex = new AnimIndex(modelRL, path);

            // 使用 TypedAnimationBuilder 创建并动态注册 TypedAnimation
            TypedAnimation animation = SparkCore.REGISTER.typedAnimation()
                .id(fullId) // 设置完整的资源位置
                .animIndex(animIndex)
                .provider(receiver -> {
                    // 可以在这里添加动画播放时的逻辑
                    return Unit.INSTANCE;
                })
                .registerDynamic(fullId); // 动态注册

            // 同步到客户端（如果在服务端注册）
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                NetworkHandler.INSTANCE.syncTypedAnimationToClients(fullId, animation);
            }

            SparkCore.LOGGER.info("SparkApi.registerTypedAnimation: Successfully dynamically registered animation '{}' with path '{}'", animationId, path);

            // 返回动画ID
            return fullId.toString();
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.registerTypedAnimation: Error registering animation", e);
            return null;
        }
    }

    /**
     * 播放已注册的TypedAnimation
     *
     * @param UUID 实体 UUID
     * @param animationId 动画ID，由registerTypedAnimation方法返回
     * @param duration 动画持续时间（单位：tick）
     * @return 是否成功播放动画
     */
    public boolean playTypedAnimation(String UUID, String animationId, int duration) {
        IAnimatable<?> animatable = findAnimatable(UUID);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.playTypedAnimation: Animatable with id '{}' not found", UUID);
            return false;
        }

        if (!(animatable instanceof IEntityAnimatable)) {
            SparkCore.LOGGER.error("SparkApi.playTypedAnimation: Animatable must be an entity type");
            return false;
        }

        try {
            // 查找注册的TypedAnimation
            ResourceLocation animationResLoc = ResourceLocation.parse(animationId);
            TypedAnimation animation = SparkRegistries.getTYPED_ANIMATION().get(animationResLoc);
            if (animation == null) {
                SparkCore.LOGGER.error("SparkApi.playTypedAnimation: Animation with id '{}' not found", animationId);
                return false;
            }

            // 播放动画
            animation.play(animatable, duration, false);

            // 如果是服务端，同步到客户端
            if (!animatable.getAnimLevel().isClientSide()) {
                Entity entity = ((IEntityAnimatable<?>) animatable).getAnimatable();
                animation.syncToClient(entity.getId(), duration, false, null);
            }

            return true;
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.playTypedAnimation: Error playing animation", e);
            return false;
        }
    }


    /**
     * Plays an animation for a specified model.
     *
     * @param modelId The unique identifier of the model/entity to animate.
     * @param animationName The name of the animation to play.
     */
    /**
     * 播放模型动画
     *
     * @param UUID entity的UUID
     * @param namespace 资源命名空间，默认为"player"
     * @param animationName 动画名称，如 "attack"
     */
    public void playAnimation(String UUID, String namespace, String animationName) {
        IAnimatable<?> animatable = findAnimatable(UUID);
        if (animatable != null) {
            // 生成唯一的动画ID，格式为 namespace:animationName
            String animationId = namespace + "_" + animationName;

            // 注册动画（如果已注册则会返回已注册的动画）
            String fullId =  registerTypedAnimation(animationId, namespace, animationName);

            // 播放动画
            playTypedAnimation(UUID, fullId, 0);
        } else {
            SparkCore.LOGGER.error("Cannot play animation: model '{}' not found", UUID);
        }
    }
}