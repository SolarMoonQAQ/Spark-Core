package cn.solarmoon.spark_core.js.ik

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.JointConstraint // Import needed if handling constraints here
import cn.solarmoon.spark_core.ik.component.IKComponentType // Keep this import
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.HostAccess

/**
 * Builder used by JS scripts to configure a new IKComponentType.
 */
class JSIKComponentTypeBuilder {
    // Properties settable from JS
    lateinit var id: ResourceLocation
    lateinit var chainName: String
    lateinit var startBoneName: String
    lateinit var endBoneName: String
    var bonePathNames: List<String>? = null // Add property for explicit path
    var defaultTolerance: Float = 0.1f
    var defaultMaxIterations: Int = 15
    var priority: Int = 0 // For registration order if needed

    @HostAccess.Export fun setId(v: String) { id = ResourceLocation.parse(v) } // Allow setting ID via string
    @HostAccess.Export fun setIKChainName(v: String) { chainName = v }
    @HostAccess.Export fun setStartBone(v: String) { startBoneName = v }
    @HostAccess.Export fun setEndBone(v: String) { endBoneName = v }
    // Method for JS to set the explicit bone path. Takes a JS array, converts to Kotlin List.
    @HostAccess.Export fun setBonePath(path: Array<String>) {
        bonePathNames = path.toList().takeIf { it.isNotEmpty() } // Store as list, null if empty
    }
    @HostAccess.Export fun setTolerance(v: Float) { defaultTolerance = v }
    @HostAccess.Export fun setMaxIterations(v: Int) { defaultMaxIterations = v }
    @HostAccess.Export fun setIKPriority(v: Int) { priority = v }

    // Creates the IKComponentType instance from the collected data. Registration happens elsewhere.
    fun build(): IKComponentType? {
        // Basic validation
        if (!::id.isInitialized || !::chainName.isInitialized || !::startBoneName.isInitialized || !::endBoneName.isInitialized) {
            SparkCore.LOGGER.error("JS IKComponentTypeBuilder: Missing required fields (id, chainName, startBoneName, endBoneName) for potential ID $id")
            return null
        }
        // Pass all collected data to the IKComponentType constructor
        // Note: Assumes jointConstraints are handled separately or default to emptyMap() for now.
        // If constraints need to be built here, add relevant properties and methods.
        val type = IKComponentType(
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