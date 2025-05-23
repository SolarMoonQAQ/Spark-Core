package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class DynamicIKConstraintHandler(private val baseIKPath: Path) : IDynamicResourceHandler {

    init {
        if (!Files.isDirectory(baseIKPath)) {
            throw IllegalArgumentException("Base path '$baseIKPath' must be a directory and exist.")
        }
        SparkCore.LOGGER.info("DynamicIKConstraintHandler initialized for path: $baseIKPath")
    }

    override fun getDirectoryPath(): String {
        return baseIKPath.toString()
    }

    override fun getResourceType(): String {
        return "ik_constraint"
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseIKPath)) {
            SparkCore.LOGGER.warn("File $file is not under base path $baseIKPath. Cannot determine ResourceLocation.")
            return null
        }

        val relativePath = baseIKPath.relativize(file)
        
        // IK constraints are typically flat in their directory, e.g., geo/ik_constraints/player_ik.json
        // So, the filename without extension becomes the path of the ResourceLocation.
        if (relativePath.parent == null) { 
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // Or your mod's namespace
            val pathName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            return ResourceLocation.fromNamespaceAndPath(namespace, pathName)
        } else { 
            // This case should ideally not happen based on IKConstraintListener.kt structure.
            // If it does, we'll use the first directory component as the root.
            SparkCore.LOGGER.warn("File $file is in a subdirectory ${relativePath.parent} under $baseIKPath. This is unusual for IK constraints. Using first directory as root.")
            val rootDirName = relativePath.getName(0).toString().lowercase().replace(" ", "_")
            val namespace = ResourceLocation.DEFAULT_NAMESPACE 
            return ResourceLocation.fromNamespaceAndPath(namespace, rootDirName)
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("Attempting to add IK constraint(s) from file: $file")
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for added file: $file")
            return
        }

        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            
            if (!jsonElement.isJsonArray) {
                SparkCore.LOGGER.error("IK constraint file $file for root '$root' is not a JSON array. Skipping.")
                return
            }
            val jsonArray = jsonElement.asJsonArray
            if (jsonArray.isEmpty) {
                 SparkCore.LOGGER.warn("IK constraint file $file for root '$root' is an empty JSON array. No constraints to load.")
                return
            }

            var constraintCount = 0
            jsonArray.forEach { constraintJsonElement ->
                val parsedConstraint = OIKConstraint.CODEC.decode(JsonOps.INSTANCE, constraintJsonElement).orThrow.first
                
                // This will overwrite previous constraints from the same file for the same root.
                OIKConstraint.ORIGINS[root] = parsedConstraint
                constraintCount++
            }

            if (constraintCount > 0) {
                SparkCore.LOGGER.info("Successfully loaded $constraintCount IK constraint(s) for '$root' from $file. The last one in the file is active for this root.")
                if (constraintCount > 1) {
                    SparkCore.LOGGER.warn("File $file contained $constraintCount IK constraints for root '$root'. Only the last one defined in the JSON array is stored in OIKConstraint.ORIGINS for this root.")
                }
                SparkCore.LOGGER.info("IK Constraint added/updated: $root. Network sync placeholder.")
            } else {
                 SparkCore.LOGGER.warn("No IK constraints were successfully parsed from $file for root '$root'.")
            }

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Error processing IK constraint file for addition $file (root: $root): ${e.message}", e)
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("Attempting to update IK constraint(s) from file: $file")
        // Modification is handled the same way as addition: re-read and replace.
        onResourceAdded(file) 
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("Attempting to remove IK constraint from file: $file")
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for removed file: $file. Cannot process removal.")
            return
        }

        val removedConstraint = OIKConstraint.ORIGINS.remove(root)
        if (removedConstraint != null) {
            SparkCore.LOGGER.info("Removed IK constraint for root '$root' due to file removal: $file.")
        } else {
            SparkCore.LOGGER.warn("Attempted to remove IK constraint for root '$root' (from file $file), but no constraint was found for this root.")
        }
        SparkCore.LOGGER.info("IK Constraint removed: $root. Network sync placeholder.")
    }
}
