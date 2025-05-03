package cn.solarmoon.spark_core.js.ik

// Import your network handler if needed
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.sync.RequestIKComponentChangePayload
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload
import cn.solarmoon.spark_core.js.extension.JSEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor


/*
 * Provides JS bindings for interacting with IK components on an IEntityAnimatable (Entity).
 * An instance of this might be attached to JSEntity if the entity is an IEntityAnimatable.
 * IMPORTANT: Methods here run on the client side when called from JS. Use packets for server-side actions.
 */
class JSIKHostAPI(private val jsEntity: JSEntity) { // Assumes it's accessed via a JSEntity wrapper

    // Helper to safely get the host and cast it
    private fun getHost(): IEntityAnimatable<*>? {
        val entity = jsEntity.entity
        return entity as IEntityAnimatable<*>
    }

    // Helper to safely get the host as an Entity
    private fun getHostEntity(): Entity? {
        // Ensure the entity reference within JSEntity is valid
        return jsEntity.entity as Entity?
    }

    /** Requests the server to add an IK component to the entity. */
    
    fun addIKComponent(componentTypeIdStr: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS addIKComponent: Could not find entity for JSEntity.")
            return
        }
        // No need to check getHost() here, server will validate if it's an IEntityAnimatable<*>

        val typeId = ResourceLocation.tryParse(componentTypeIdStr)
        if (typeId == null) {
            SparkCore.LOGGER.warn("JS addIKComponent: Invalid ResourceLocation string '$componentTypeIdStr'.")
            return
        }

        // This code runs on the CLIENT when called from JS.
        // It needs to send a request to the SERVER.
        val packet = RequestIKComponentChangePayload(hostEntity.id, typeId, true) // true for add

        PacketDistributor.sendToServer(packet) // Use your actual network sending logic
        SparkCore.LOGGER.info("[CLIENT JS] Requesting add IK component $typeId to entity ${hostEntity.id}") // Placeholder log
    }

    /** Requests the server to remove an IK component from the entity. */
    
    fun removeIKComponent(componentTypeIdStr: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS removeIKComponent: Could not find entity for JSEntity.")
            return
        }

        val typeId = ResourceLocation.tryParse(componentTypeIdStr)
        if (typeId == null) {
            SparkCore.LOGGER.warn("JS removeIKComponent: Invalid ResourceLocation string '$componentTypeIdStr'.")
            return
        }

        // This code runs on the CLIENT when called from JS.
        // It needs to send a request to the SERVER.
        val packet = RequestIKComponentChangePayload(hostEntity.id, typeId, false) // false for remove
        PacketDistributor.sendToServer(packet) // Use your actual network sending logic
        SparkCore.LOGGER.info("[CLIENT JS] Requesting remove IK component $typeId from entity ${hostEntity.id}") // Placeholder log
    }

    /**
     * Requests the server to set the target position for a specific IK chain on the entity.
     * The actual update happens when the server processes the request and syncs back.
     */
    
    fun setIKTarget(chainName: String, x: Double, y: Double, z: Double) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS setIKTarget: Could not find entity for JSEntity.")
            return
        }

        // Send request to server
        val targetPos = Vec3(x, y, z)
        // Assuming SetIKTargetPayload exists and takes (entityId, chainName, targetPosition)
        val packet = IKSyncTargetPayload(hostEntity.id, chainName, targetPos)
        PacketDistributor.sendToServer(packet)
        SparkCore.LOGGER.info("[CLIENT JS] Requesting set IK target for chain '$chainName' on entity ${hostEntity.id} to $targetPos")

        // Optional: Client-side prediction (apply locally immediately for smoother visuals)
        // This requires the client-side entity to actually be an IEntityAnimatable<*> and have the target map
        // val host = getHost()
        // host?.ikTargetPositions?.put(chainName, targetPos) // Apply locally if desired
    }

    /**
     * Requests the server to clear the target position for a specific IK chain on the entity.
     * The actual update happens when the server processes the request and syncs back.
     */
    
    fun clearIKTarget(chainName: String) {
        val hostEntity = getHostEntity()
        if (hostEntity == null) {
            SparkCore.LOGGER.warn("JS clearIKTarget: Could not find entity for JSEntity.")
            return
        }
        // Send request to server (using the same sync, but with a null or optional target)
        // Assuming SetIKTargetPayload handles null Vec3 for clearing, or has a separate mechanism
        val packet = IKSyncTargetPayload(hostEntity.id, chainName, null) // Pass null Vec3 to indicate clearing
        PacketDistributor.sendToServer(packet)
        SparkCore.LOGGER.info("[CLIENT JS] Requesting clear IK target for chain '$chainName' on entity ${hostEntity.id}")

        // Optional: Client-side prediction
         val host = getHost()
         host?.ikTargetPositions?.remove(chainName) // Apply locally if desired
    }
}