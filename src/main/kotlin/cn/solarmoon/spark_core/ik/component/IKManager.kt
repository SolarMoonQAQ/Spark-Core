package cn.solarmoon.spark_core.ik.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.payload.IKComponentSyncPayload
import cn.solarmoon.spark_core.physics.level.PhysicsWorld
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active IKComponents for a specific IKHost.
 */
class IKManager(private val host: IKHost<*>) { // Takes the host instance

    internal val activeComponents = ConcurrentHashMap<String, IKComponent>() // Map chainName to component

    /**
     * Adds and initializes an IK component based on its type.
     * Returns true if successful, false otherwise (e.g., model not ready, build failed).
     * This method should primarily be called on the logical server.
     */
    fun addComponent(type: IKComponentType): Boolean {
        if (activeComponents.containsKey(type.chainName)) {
            // Already added locally, maybe due to client prediction or receiving sync packet again
            // SparkCore.LOGGER.warn("IKManager: Component for chain '${type.chainName}' already exists on ${host.animatable}.")
            return true // Already added
        }

        val model = host.model

        val chain = type.buildChain(host, model) ?: run {
            SparkCore.LOGGER.error("IKManager: Failed to build chain for component '${type.id}' on ${host.animatable}.")
            return false
        }
        host.ikChains[chain.name] = chain
        val component = IKComponent(type, chain)
        activeComponents[type.chainName] = component
        SparkCore.LOGGER.info("IKManager: Added IK component '${type.id}' (chain: ${type.chainName}) to ${host.animatable}.")

        // Sync change to clients (only if running on server)
        if (!host.animLevel.isClientSide) {
            try {
                // Ensure host can be treated as an Entity for tracking
                val entityHost = host as? Entity ?: throw IllegalStateException("IKHost could not be cast to Entity for packet distribution")
                PacketDistributor.sendToPlayersTrackingEntity(entityHost, IKComponentSyncPayload(host, type, true))
            } catch (e: IllegalStateException) {
                SparkCore.LOGGER.error("IKManager: Failed to send add component sync packet.", e)
            }
        }
        return true
    }

    /**
     * Removes an active IK component by its chain name.
     * This method should primarily be called on the logical server.
     */
    fun removeComponent(chainName: String) {
        val removedComponent = activeComponents.remove(chainName)
        if (removedComponent != null) {
            SparkCore.LOGGER.info("IKManager: Removed IK component for chain '$chainName' from ${host.animatable}.")

            // Sync change to clients (only if running on server)
            if (!host.animLevel.isClientSide) {
                try {
                    // Ensure host can be treated as an Entity for tracking
                    val entityHost = host as? Entity ?: throw IllegalStateException("IKHost could not be cast to Entity for packet distribution")
                    PacketDistributor.sendToPlayersTrackingEntity(entityHost, IKComponentSyncPayload(host, removedComponent.type, false)) // Send removal sync
                } catch (e: IllegalStateException) {
                    SparkCore.LOGGER.error("IKManager: Failed to send remove component sync packet.", e)
                }
            }
        }
    }

    /**
     * Prepares IK targets by performing ground checks for relevant components.
     * This method should be called BEFORE the physics step (e.g., on the main thread)
     * after the desired target positions have been determined by animation/logic.
     *
     * @param physicsWorld The physics world instance for raycasting.
     */
    fun prepareTargetsForPhysics(physicsWorld: PhysicsWorld) {
        activeComponents.forEach { (chainName, component) ->
            // Get the desired target position set by the host (likely from animation)
            host.ikTargetPositions[chainName]?.let { desiredTargetVec3 ->
                // Convert Minecraft Vec3 to JME Vector3f for the component's method
                val desiredTargetJME = Vector3f(
                    desiredTargetVec3.x.toFloat(),
                    desiredTargetVec3.y.toFloat(),
                    desiredTargetVec3.z.toFloat()
                )
                try {
                    component.updateGroundContact(physicsWorld, desiredTargetJME)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("IKManager: Exception during preparing target for component '$chainName' for ${host.animatable}", e)
                }
            }
        }
    }


    fun getComponent(chainName: String): IKComponent? = activeComponents[chainName]
}