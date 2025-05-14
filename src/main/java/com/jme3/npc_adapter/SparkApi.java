package com.jme3.npc_adapter;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsWorld;
import net.minecraft.resources.ResourceLocation;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.world.entity.Entity;
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker;
import cn.solarmoon.spark_core.physics.presets.callback.HitReactionCollisionCallback;
import java.util.UUID;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

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
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by Int ID '" + modelId + "'.");
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
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by UUID '" + modelId + "'.");
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
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by custom name '" + modelId + "'.");
                    return (IAnimatable<?>) entity;
                }
                // Check tags
                if (entity.getTags().contains(modelId)) {
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by tag '" + modelId + "'.");
                    return (IAnimatable<?>) entity;
                }
            }
        }

        SparkCore.LOGGER.warn("SparkApi.findAnimatable: Animatable with modelId '" + modelId + "' not found in default level (overworld) as Int ID, UUID, custom name, or tag.");
        return null;
    }

    /**
     * Plays an animation for a specified model.
     *
     * @param modelId The unique identifier of the model/entity to animate.
     * @param animationName The name of the animation to play.
     * @param loop Whether the animation should loop.
     */
    public void playAnimation(String modelId, String animationName, boolean loop) {
        IAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable != null) {
            animatable.getAnimController().setAnimation(animationName, 0);
        }
    }

    /**
     * Changes the model for a given animatable entity.
     * The new model, its animations, and texture are determined by newModelPath.
     * Assumes newModelPath is a string like "namespace:path".
     */
    public void changeModel(String modelId, String newModelPath) {
        IAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Animatable with id '" + modelId + "' not found.");
            return;
        }

        try {
            ResourceLocation modelResLoc = ResourceLocation.tryParse(newModelPath);
            if (modelResLoc == null) {
                SparkCore.LOGGER.error("SparkApi.changeModel: Invalid newModelPath format '" + newModelPath + "'. Expected 'namespace:path'.");
                return;
            }

            // Assuming the animation path is the same as the model path for simplicity.
            // Texture path will be conventionally derived by ModelIndex constructor if not overridden.
            // IK path will also be inferred by ModelIndex constructor.
            // We use the ModelIndex constructor: constructor(modelPath: ResourceLocation, animPath: ResourceLocation, textureLocation: ResourceLocation)
            // For texture, we need to make a conventional one. If this is an entity, it's usually 'namespace:textures/entity/path.png'
            // For now, let's create a simple texture path based on the model path. A more robust solution might be needed.
            ResourceLocation textureResLoc = ResourceLocation.fromNamespaceAndPath(modelResLoc.getNamespace(), "textures/entity/" + modelResLoc.getPath() + ".png");
            
            ModelIndex newIdx = new ModelIndex(modelResLoc, modelResLoc, textureResLoc);

            // This is the core action that should trigger a model update and ModelIndexChangeEvent
            animatable.setModelIndex(newIdx);
            
            SparkCore.LOGGER.info("SparkApi.changeModel: Successfully requested to change model for '" + modelId + "' to '" + newModelPath + "' (ModelIndex: " + newIdx + "). findAnimatable is still a placeholder.");

        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Error changing model for '" + modelId + "' to '" + newModelPath + "': " + e.getMessage(), e);
        }
    }

    // --- Collision Event Handling --- (Task 4 & 5)

    // Helper function to find the PhysicsCollisionObject
    private PhysicsCollisionObject findPhysicsObjectForBox(IAnimatable<?> animatable, String boxName) {
        Entity targetEntity = (Entity) animatable.getAnimatable();
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: Animatable's underlying object (modelId: '" + animatable.getModelIndex().getModelPath() + "') is not an Entity or is null.");
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
        SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: No PhysicsCollisionObject found for entity " + targetEntity.getId() + " and boxName '" + boxName + "'.");
        return null;
    }

    /**
     * Sets up a callback for a specific collision box on a model.
     * When a collision occurs for this box, the specified jsFlagVariable will be set to true in an internal map.
     */
    public void setCollisionBoxCallback(String modelId, String collisionBoxName, String jsFlagVariable) {
        IAnimatable<?> animatable = findAnimatable(modelId);
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: Animatable with id '" + modelId + "' not found.");
            return;
        }

        PhysicsCollisionObject physicsObject = findPhysicsObjectForBox(animatable, collisionBoxName);
        if (physicsObject == null) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: PhysicsCollisionObject for box '" + collisionBoxName + "' on model '" + modelId + "' not found. Ensure the model and bone name are correct and physics is set up.");
            return;
        }

        HitReactionCollisionCallback callback = new HitReactionCollisionCallback() {
            // HitReactionCollisionCallback provides a default for attackSystem, so we don't need to override it here.
            // override val attackSystem: AttackSystem get() = AttackSystem() // This is provided by the interface default

            @Override
            public void onProcessed(@NotNull PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, long manifoldId) {
                // Check if the collision involves the specific physicsObject we are listening to.
                if (o1 == physicsObject || o2 == physicsObject) {
                    PhysicsCollisionObject involvedPco = (o1 == physicsObject) ? o1 : o2;
                    SparkCore.LOGGER.debug("SparkApi.setCollisionBoxCallback: Collision processed for model '{}', box '{}'. Setting flag '{}'. Involved: {} with {}, manifold: {}", modelId, collisionBoxName, jsFlagVariable, o1.getOwner().toString(), o2.getOwner().toString(), manifoldId);
                    synchronized (collisionEventFlags) {
                        collisionEventFlags.put(jsFlagVariable, true);
                    }
                }
                // Not calling super.onProcessed() as we only want to set the flag,
                // not trigger default attack/hit logic.
            }

            // preAttack, doAttack, postAttack have default implementations in AttackCollisionCallback,
            // so no need to override them here for simple flag setting.
        };

        try {
            physicsObject.addCollisionCallback(callback); // Assumes extension function
            SparkCore.LOGGER.info("SparkApi.setCollisionBoxCallback: Successfully set collision callback for model '{}', box '{}' to update '{}'.", modelId, collisionBoxName, jsFlagVariable);
        } catch (Exception e) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: Error adding collision callback for model '{}', box '{}': {}", modelId, collisionBoxName, e.getMessage(), e);
        }
    }

    /**
     * Retrieves the current state of all collision event flags.
     * This map is cleared after retrieval.
     */
    public Map<String, Boolean> getCollisionEventFlagsAndClear() {
        synchronized (collisionEventFlags) {
            Map<String, Boolean> flagsToReturn = new HashMap<>(collisionEventFlags);
            collisionEventFlags.clear();
            return flagsToReturn;
        }
    }

    /**
     * Retrieves the state of a specific collision flag for JS consumption.
     * Does not clear the flag.
     */
    public boolean getJsCollisionFlag(String jsFlagVariable) {
        synchronized (collisionEventFlags) {
            return collisionEventFlags.getOrDefault(jsFlagVariable, false);
        }
    }

    /**
     * Resets a specific collision flag. This typically means removing it, as absence implies false.
     */
    public void resetJsCollisionFlag(String jsFlagVariable) {
        synchronized (collisionEventFlags) {
            collisionEventFlags.remove(jsFlagVariable);
        }
        SparkCore.LOGGER.debug("SparkApi: JS flag '" + jsFlagVariable + "' was reset.");
    }
}