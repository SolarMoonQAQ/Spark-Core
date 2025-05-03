package cn.solarmoon.spark_core.ik.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.ik.sync.IKSyncTargetPayload // Import S2C sync sync
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor // Import PacketDistributor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3 // Import Vec3
import net.minecraft.world.entity.Entity

/**
 * Central service for handling authoritative IK component logic on the server.
 */
object IKService {

    /**
     * Handles a request (e.g., from a network packet or command) to add or remove an IK component.
     * Performs validation and calls the IKManager.
     */
    fun handleComponentChangeRequest(
        level: ServerLevel,
        requester: ServerPlayer?, // Optional: for permission checks
        targetEntityId: Int,
        componentTypeId: ResourceLocation,
        addComponent: Boolean
    ) {
        val targetEntity = level.getEntity(targetEntityId)
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("IKService: Cannot process request. Entity $targetEntityId not found in level ${level.dimension().location()}.")
            return
        }

        val host = targetEntity as? IEntityAnimatable<*> ?: run { // Use non-generic IEntityAnimatable
            SparkCore.LOGGER.warn("IKService: Cannot process request. Entity $targetEntityId (${targetEntity.type.descriptionId}) is not an IEntityAnimatable.")
            return
        }

        // TODO: Implement permission checks based on 'requester' if needed
        // Example: if (requester == null || !requester.hasPermissions(REQUIRED_PERMISSION_LEVEL)) return

        val componentType = SparkRegistries.IK_COMPONENT_TYPE.get(componentTypeId) ?: run { // Access registry via .get()
            SparkCore.LOGGER.warn("IKService: Cannot process request. Unknown IKComponentType ID: $componentTypeId")
            return
        }

        // Call the appropriate IKManager method (which handles sync packet sending)
        if (addComponent) {
            host.ikManager.addComponent(componentType)
        } else {
            // Ensure removal uses the correct identifier (chainName from type)
            host.ikManager.removeComponent(componentType.chainName)
        }
    }

    /**
     * Handles a request to set or clear an IK target position for a specific chain.
     * Updates the server-side authoritative target map.
     */
    fun handleSetIKTargetRequest(
        level: ServerLevel,
        requester: ServerPlayer?, // Optional: for permission checks
        targetEntityId: Int,
        chainName: String,
        targetPosition: Vec3? // Nullable: null means clear
    ) {
        val targetEntity = level.getEntity(targetEntityId)
        if (targetEntity == null) {
            SparkCore.LOGGER.warn("IKService: Cannot set target. Entity $targetEntityId not found.")
            return
        }
        val host = targetEntity as? IEntityAnimatable<*> ?: run {
             // Log if entity found but not IEntityAnimatable
             SparkCore.LOGGER.warn("IKService: Cannot set target. Entity $targetEntityId (${targetEntity.type.descriptionId}) is not an IEntityAnimatable.")
             return
        }

        // TODO: Implement permission checks if needed

        if (targetPosition != null) {
            host.ikTargetPositions[chainName] = targetPosition // Update server map
            SparkCore.LOGGER.debug("IKService: Set target for chain '$chainName' on entity $targetEntityId to $targetPosition")
        } else {
            host.ikTargetPositions.remove(chainName) // Clear target from server map
            SparkCore.LOGGER.debug("IKService: Cleared target for chain '$chainName' on entity $targetEntityId")
        }

        // --- Send S2C IKSyncTargetPayload packet to sync clients ---
        try {
            // Explicitly get entity ID and call the primary constructor to avoid potential resolution issues
            val entityIdForPacket = (host as? Entity)?.id ?: run {
                SparkCore.LOGGER.error("IKService: Could not get entity ID from IEntityAnimatable for sync packet.")
                return // Don't proceed if we can't get a valid ID
            }
            // Call the primary constructor directly
            val syncPacket = IKSyncTargetPayload(entityIdForPacket, chainName, targetPosition)
            PacketDistributor.sendToPlayersTrackingEntity(targetEntity, syncPacket)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("IKService: Failed to send IKSyncTargetPayload for entity $targetEntityId, chain '$chainName'", e)
        }
    }

    // TODO: Add other server-side IK logic methods here as needed
    // For example, methods triggered by AI, game events, commands, etc.
    // fun addComponentFromServer(entity: Entity, typeId: ResourceLocation) { ... }
}