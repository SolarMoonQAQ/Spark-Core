package cn.solarmoon.spark_core.resource.handler

import java.nio.file.Path
import net.minecraft.resources.ResourceLocation

interface IDynamicResourceHandler {
    /** Called when a new resource file is added. */
    fun onResourceAdded(file: Path)

    /** Called when an existing resource file is modified. */
    fun onResourceModified(file: Path)

    /** Called when a resource file is removed. */
    fun onResourceRemoved(file: Path)

    /** 
     * Gets the unique identifier for the directory this handler will monitor.
     * This ID is relative to a base 'sparkcore' directory (e.g., "animations", "models").
     * The handler is responsible for constructing its full path using this ID.
     */
    fun getDirectoryId(): String

    /** 
     * (Optional) Gets the ResourceLocation of a dynamic registry this handler is associated with, if any.
     * This can be used for context or linking the handler to specific game data.
     */
    fun getRegistryIdentifier(): ResourceLocation?

    /** 
     * Gets the absolute root directory path this handler is responsible for. 
     * Note: With the introduction of getDirectoryId(), this method's role might need re-evaluation 
     * as handlers are expected to construct their paths using getDirectoryId().
     */
    fun getDirectoryPath(): String 

    /** Gets the type of resource this handler manages (e.g., "animation", "model"). */
    fun getResourceType(): String

    /**
     * Retrieves the name of the source directory within the JAR for default resources.
     * By default, this is the same as the directory ID.
     *
     * @return The source directory name as a String.
     */
    fun getSourceResourceDirectoryName(): String = getDirectoryId()

    /**
     * Initializes default resources for this handler, e.g., by copying them from the JAR to the runtime directory.
     * This method is called by the HandlerDiscoveryService during mod initialization.
     *
     * @param modMainClass The main class of the mod, used to access JAR resources via its classloader.
     * @return True if initialization was successful or not needed, false otherwise.
     */
    fun initializeDefaultResources(modMainClass: Class<*>): Boolean = true
}
