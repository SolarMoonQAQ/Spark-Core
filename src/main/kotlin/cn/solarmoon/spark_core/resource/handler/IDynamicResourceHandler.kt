package cn.solarmoon.spark_core.resource.handler

import java.nio.file.Path

interface IDynamicResourceHandler {
    /** Called when a new resource file is added. */
    fun onResourceAdded(file: Path)

    /** Called when an existing resource file is modified. */
    fun onResourceModified(file: Path)

    /** Called when a resource file is removed. */
    fun onResourceRemoved(file: Path)

    /** Gets the absolute root directory path this handler is responsible for. */
    fun getDirectoryPath(): String // Or Path, but String matches plan

    /** Gets the type of resource this handler manages (e.g., "animation", "model"). */
    fun getResourceType(): String
}
