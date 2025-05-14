package cn.solarmoon.spark_core.npc_adapter

import cn.solarmoon.spark_core.SparkCore // For logging
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import net.minecraft.resources.ResourceLocation
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.presets.callback.HitReactionCollisionCallback
import java.util.UUID // For parsing UUID strings
import net.neoforged.neoforge.server.ServerLifecycleHooks


/**
 * Spark Core Native API for NPC Adapters.
 * Provides functionalities like animation control, model swapping, and collision event handling,
 * independent of WebSocket and JS-bridge implementations.
 */
class SparkApiBak {

    // Stores collision event flags. Key: jsFlagVariable, Value: true if collision occurred
    private val collisionEventFlags = mutableMapOf<String, Boolean>()

    init {
        // Initialization logic, if any (e.g., logging)
        // SparkCore.LOGGER.info("SparkApi (Native NPC Adapter) initialized.")
    }

    // Map to store model ID to IAnimatable instances (if needed for caching or specific lookup)
    // private val animatableRegistry = mutableMapOf<String, IAnimatable<*>>()

    // Placeholder - needs actual implementation
    private fun findAnimatable(modelId: String): IAnimatable<*>? {
        val server = ServerLifecycleHooks.getCurrentServer() // Use NeoForge API
        if (server == null) {
            SparkCore.LOGGER.error("SparkApi.findAnimatable: MinecraftServer instance not available via ServerLifecycleHooks.")
            return null
        }

        val level = server.overworld() // Get Overworld as default
        if (level == null) {
            SparkCore.LOGGER.error("SparkApi.findAnimatable: Overworld ServerLevel not available from server instance.")
            return null
        }

        // 1. Try parsing modelId as an Integer (Entity ID)
        modelId.toIntOrNull()?.let { entityIdInt ->
            val entity = level.getEntity(entityIdInt)
            if (entity is IAnimatable<*>) {
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by Int ID '$modelId'.")
                return entity
            }
        }

        // 2. Try parsing modelId as a UUID string
        try {
            val uuid = UUID.fromString(modelId)
            val entity = level.getEntity(uuid)
            if (entity is IAnimatable<*>) {
                SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by UUID '$modelId'.")
                return entity
            }
        } catch (e: IllegalArgumentException) {
            // Not a valid UUID, normal, proceed to next checks
        }

        // 3. Try searching by custom name or tag (can be slow, consider if this is common)
        // Iterating all entities is resource-intensive. Prefer direct ID/UUID lookup.
        // If custom names/tags are primary identifiers, a dedicated registry would be more efficient.
        for (entity in level.allEntities) { // Iterate all entities in the level
            if (entity is IAnimatable<*>) {
                // Check custom name
                if (entity.customName?.string == modelId) {
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by custom name '$modelId'.")
                    return entity
                }
                // Check tags
                if (entity.tags.contains(modelId)) {
                    SparkCore.LOGGER.debug("SparkApi.findAnimatable: Found animatable by tag '$modelId'.")
                    return entity
                }
            }
        }

        SparkCore.LOGGER.warn("SparkApi.findAnimatable: Animatable with modelId '$modelId' not found in default level (overworld) as Int ID, UUID, custom name, or tag.")
        return null
    }

    /**
     * Plays an animation for a specified model.
     *
     * @param modelId The unique identifier of the model/entity to animate.
     * @param animationName The name of the animation to play.
     * @param loop Whether the animation should loop.
     */
    fun playAnimation(modelId: String, animationName: String) {
        val animatable = findAnimatable(modelId)
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.playAnimation: Animatable with id '$modelId' not found.")
            return
        }

        val originalOAnimation = animatable.animations.getAnimation(animationName)
        if (originalOAnimation == null) {
            SparkCore.LOGGER.error("SparkApi.playAnimation: OAnimation named '$animationName' not found for model '$modelId'.")
            return
        }

        try {
            // Create a new OAnimation with the desired loop setting

            // Create an AnimInstance with the modified OAnimation
            // The AnimInstance.create method does not require a modifier lambda here as loop is set on OAnimation
            val animInstance = AnimInstance.create(animatable, animationName)
            
            // Set the animation using the AnimController
            // The transition time is set to 0 for immediate animation changes.
            animatable.animController.setAnimation(animInstance, 0)

        } catch (e: Exception) {
            SparkCore.LOGGER.error("SparkApi.playAnimation: Error playing animation '$animationName' on model '$modelId': ${e.message}", e)
        }
    }

    /**
     * Changes the model for a given animatable entity.
     * The new model, its animations, and texture are determined by newModelPath.
     * Assumes newModelPath is a string like "namespace:path".
     */
    fun changeModel(modelId: String, newModelPath: String) {
        val animatable = findAnimatable(modelId)
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Animatable with id '$modelId' not found.")
            return
        }

        try {
            val modelResLoc = ResourceLocation.tryParse(newModelPath)
            if (modelResLoc == null) {
                SparkCore.LOGGER.error("SparkApi.changeModel: Invalid newModelPath format '$newModelPath'. Expected 'namespace:path'.")
                return
            }

            // Assuming the animation path is the same as the model path for simplicity.
            // Texture path will be conventionally derived by ModelIndex constructor if not overridden.
            // IK path will also be inferred by ModelIndex constructor.
            // We use the ModelIndex constructor: constructor(modelPath: ResourceLocation, animPath: ResourceLocation, textureLocation: ResourceLocation)
            // For texture, we need to make a conventional one. If this is an entity, it's usually 'namespace:textures/entity/path.png'
            // For now, let's create a simple texture path based on the model path. A more robust solution might be needed.
            val textureResLoc = ResourceLocation.fromNamespaceAndPath(modelResLoc.namespace, "textures/entity/${modelResLoc.path}.png")
            
            val newIdx = ModelIndex(modelResLoc, modelResLoc, textureResLoc)

            // This is the core action that should trigger a model update and ModelIndexChangeEvent
            animatable.modelIndex = newIdx
            
            SparkCore.LOGGER.info("SparkApi.changeModel: Successfully requested to change model for '$modelId' to '$newModelPath' (ModelIndex: $newIdx). findAnimatable is still a placeholder.")

        } catch (e: Exception) {
            SparkCore.LOGGER.error("SparkApi.changeModel: Error changing model for '$modelId' to '$newModelPath': ${e.message}", e)
        }
    }

    // --- Collision Event Handling --- (Task 4 & 5)

    // Helper function to find the PhysicsCollisionObject
    private fun findPhysicsObjectForBox(animatable: IAnimatable<*>, boxName: String): PhysicsCollisionObject? {
        val targetEntity = animatable.animatable as? Entity
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: Animatable's underlying object (modelId: '${animatable.modelIndex.modelPath}') is not an Entity or is null.")
            return null
        }

        val physicsLevel = targetEntity.level().physicsLevel
        // physicsLevel.world should be PhysicsWorld, which has pcoList
        val physicsWorld = physicsLevel.world

        for (pco in physicsWorld.pcoList) { // pcoList is from PhysicsWorld
            val pcoOwner = pco.owner // Assumes pco.owner gives the Entity
            if (pcoOwner == targetEntity) {
                for (ticker in pco.tickers) { // Assumes pco.tickers is available
                    if (ticker is MoveWithAnimatedBoneTicker) {
                        if (ticker.boneName == boxName) {
                            return pco // Found the matching PhysicsCollisionObject
                        }
                    }
                }
            }
        }
        SparkCore.LOGGER.warn("SparkApi.findPhysicsObjectForBox: No PhysicsCollisionObject found for entity ${targetEntity.id} and boxName '$boxName'.")
        return null
    }

    /**
     * Sets up a callback for a specific collision box on a model.
     * When a collision occurs for this box, the specified jsFlagVariable will be set to true in an internal map.
     */
    fun setCollisionBoxCallback(modelId: String, collisionBoxName: String, jsFlagVariable: String) {
        val animatable = findAnimatable(modelId)
        if (animatable == null) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: Animatable with id '$modelId' not found.")
            return
        }

        val physicsObject = findPhysicsObjectForBox(animatable, collisionBoxName)
        if (physicsObject == null) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: PhysicsCollisionObject for box '$collisionBoxName' on model '$modelId' not found. Ensure the model and bone name are correct and physics is set up.")
            return
        }

        val callback = object : HitReactionCollisionCallback {
            // HitReactionCollisionCallback provides a default for attackSystem, so we don't need to override it here.
            // override val attackSystem: AttackSystem get() = AttackSystem() // This is provided by the interface default

            override fun onProcessed(
                o1: PhysicsCollisionObject,
                o2: PhysicsCollisionObject,
                manifoldId: Long
            ) {
                // Check if the collision involves the specific physicsObject we are listening to.
                if (o1 == physicsObject || o2 == physicsObject) {
                    val involvedPco = if (o1 == physicsObject) o1 else o2
                    if (involvedPco == physicsObject) {
                        SparkCore.LOGGER.debug("SparkApi.setCollisionBoxCallback: Collision processed for model '$modelId', box '$collisionBoxName'. Setting flag '$jsFlagVariable'. Involved: ${o1.owner?.toString()} with ${o2.owner?.toString()}, manifold: $manifoldId")
                        synchronized(collisionEventFlags) {
                            collisionEventFlags[jsFlagVariable] = true
                        }
                    }
                }
                // Not calling super.onProcessed() as we only want to set the flag,
                // not trigger default attack/hit logic.
            }

            // preAttack, doAttack, postAttack have default implementations in AttackCollisionCallback,
            // so no need to override them here for simple flag setting.
        }

        try {
            physicsObject.addCollisionCallback(callback) // Assumes extension function
            SparkCore.LOGGER.info("SparkApi.setCollisionBoxCallback: Successfully set collision callback for model '$modelId', box '$collisionBoxName' to update '$jsFlagVariable'.")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("SparkApi.setCollisionBoxCallback: Error adding collision callback for model '$modelId', box '$collisionBoxName': ${e.message}", e)
        }
    }

    /**
     * Retrieves the current state of all collision event flags.
     * This map is cleared after retrieval.
     */
    fun getCollisionEventFlagsAndClear(): Map<String, Boolean> {
        synchronized(collisionEventFlags) {
            val flagsToReturn = HashMap(collisionEventFlags)
            collisionEventFlags.clear()
            return flagsToReturn
        }
    }

    /**
     * Retrieves the state of a specific collision flag for JS consumption.
     * Does not clear the flag.
     */
    fun getJsCollisionFlag(jsFlagVariable: String): Boolean {
        synchronized(collisionEventFlags) {
            return collisionEventFlags.getOrDefault(jsFlagVariable, false)
        }
    }

    /**
     * Resets a specific collision flag. This typically means removing it, as absence implies false.
     */
    fun resetJsCollisionFlag(jsFlagVariable: String) {
        synchronized(collisionEventFlags) {
            collisionEventFlags.remove(jsFlagVariable)
        }
        SparkCore.LOGGER.debug("SparkApi: JS flag '$jsFlagVariable' was reset.")
    }
}
