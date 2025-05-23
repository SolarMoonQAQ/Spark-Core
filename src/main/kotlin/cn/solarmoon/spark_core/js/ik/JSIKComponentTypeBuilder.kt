package cn.solarmoon.spark_core.js.ik

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import net.minecraft.resources.ResourceLocation

/**
 * Builder used by JS scripts to configure a new TypedIKComponent.
 */
class JSIKComponentTypeBuilder {
    // Properties settable from JS
    lateinit var id: ResourceLocation
    lateinit var chainName: String
    lateinit var startBoneName: String
    lateinit var endBoneName: String
    lateinit var bonePathNames: List<String> // Add property for explicit path
    var defaultTolerance: Float = 0.1f
    var defaultMaxIterations: Int = 15
    var priority: Int = 0 // For registration order if needed

     fun setId(v: String) { id = ResourceLocation.parse(v) } // Allow setting ID via string
     fun setIKChainName(v: String) { chainName = v }
     fun setStartBone(v: String) { startBoneName = v }
     fun setEndBone(v: String) { endBoneName = v }
    // Method for JS to set the explicit bone path. Takes a JS array, converts to Kotlin List.
     fun setBonePath(path: Array<String>) {
        bonePathNames = path.toList() // Store as list, null if empty
    }
     fun setTolerance(v: Float) { defaultTolerance = v }
     fun setMaxIterations(v: Int) { defaultMaxIterations = v }
     fun setIKPriority(v: Int) { priority = v }

    // Creates the TypedIKComponent instance from the collected data. Registration happens elsewhere.
    fun build(): TypedIKComponent? {
        // Basic validation
        if (!::id.isInitialized || !::chainName.isInitialized || !::startBoneName.isInitialized || !::endBoneName.isInitialized) {
            SparkCore.LOGGER.error("JS IKComponentTypeBuilder: Missing required fields (id, chainName, startBoneName, endBoneName) for potential ID $id")
            return null
        }
        // Pass all collected data to the TypedIKComponent constructor
        // Note: Assumes jointConstraints are handled separately or default to emptyMap() for now.
        // If constraints need to be built here, add relevant properties and methods.
        val type = TypedIKComponent(
            id = id,
            chainName = chainName,
            startBoneName = startBoneName,
            endBoneName = endBoneName,
            bonePathNames = bonePathNames, // Pass the explicit path if set
            defaultTolerance = defaultTolerance,
            defaultMaxIterations = defaultMaxIterations
            // jointConstraints = buildConstraints() // Example if constraints were built here
        )
        return type
    }
}