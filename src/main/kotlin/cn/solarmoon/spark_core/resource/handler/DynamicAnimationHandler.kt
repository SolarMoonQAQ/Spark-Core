package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class DynamicAnimationHandler(private val baseAnimationPath: Path) : IDynamicResourceHandler {

    init {
        if (!Files.isDirectory(baseAnimationPath)) {
            throw IllegalArgumentException("Base path '$baseAnimationPath' must be a directory and exist.")
        }
        SparkCore.LOGGER.info("DynamicAnimationHandler initialized for path: $baseAnimationPath")
    }

    override fun getDirectoryPath(): String {
        return baseAnimationPath.toString()
    }

    override fun getResourceType(): String {
        return "animation"
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseAnimationPath)) {
            SparkCore.LOGGER.warn("File $file is not under base path $baseAnimationPath. Cannot determine ResourceLocation.")
            return null
        }

        val relativePath = baseAnimationPath.relativize(file)
        
        if (relativePath.parent == null) { // File is directly under baseAnimationPath, e.g., base/custom_mob.json
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // Or your mod's namespace
            val pathName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            return ResourceLocation.fromNamespaceAndPath(namespace, pathName)
        } else { // File is in a subdirectory, e.g., base/player/walk.json
            // The first component of the relative path is the root
            val rootDirName = relativePath.getName(0).toString().lowercase().replace(" ", "_")
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // Or your mod's namespace
            return ResourceLocation.fromNamespaceAndPath(namespace, rootDirName)
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("Attempting to add animation from file: {}", file)
        val root = determineResourceLocationRoot(file)
        if (root == null) {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for added file: $file")
            return
        }

        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            val newSet = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, jsonElement).orThrow.first

            OAnimationSet.ORIGINS.compute(root) { _, existingSet ->
                existingSet?.apply {
                    this.animations.putAll(newSet.animations)
                    SparkCore.LOGGER.info("Applied ${newSet.animations.size} new/updated animations to existing set for root '$root' from file $file.")
                } ?: run {
                    SparkCore.LOGGER.info("Created new animation set for root '$root' with ${newSet.animations.size} animations from file $file.")
                    newSet
                }
            }
            SparkCore.LOGGER.info("Successfully added/merged animations for '$root' from $file.")
            SparkCore.LOGGER.info("Animation added/updated: $root. Network sync placeholder.")

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Error processing animation file for addition $file (root: $root): ${e.message}", e)
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("Attempting to update animation from file: {}", file)
        val root = determineResourceLocationRoot(file)
        if (root == null) {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for modified file: $file")
            return
        }

        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            val newSet = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, jsonElement).orThrow.first

            // This logic effectively replaces or adds, similar to onResourceAdded's compute.
            // If a more granular update within an existing OAnimationSet is needed (e.g. only update specific animations from the file),
            // the logic here would need to be more complex. For now, re-merging the whole file's content is standard.
            OAnimationSet.ORIGINS.compute(root) { _, existingSet ->
                 existingSet?.apply {
                    // Clear existing animations that might have come from this root? 
                    // No, the spec implies merging, similar to EntityAnimListener.
                    // If this file is the SOLE source for 'root', then it's effectively an overwrite of 'root's animations.
                    // If multiple files contribute to 'root', this merges.
                    this.animations.putAll(newSet.animations) 
                    SparkCore.LOGGER.info("Applied ${newSet.animations.size} new/updated animations to existing set for root '$root' from file $file during modification.")
                } ?: run {
                    SparkCore.LOGGER.info("Created new animation set for root '$root' with ${newSet.animations.size} animations from file $file during modification (treated as new).")
                    newSet
                }
            }
            SparkCore.LOGGER.info("Successfully updated/merged animations for '$root' from $file.")
            SparkCore.LOGGER.info("Animation updated: $root. Network sync placeholder.")

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Error processing animation file for modification $file (root: $root): ${e.message}", e)
        }
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("Attempting to remove animation from file: {}", file)
        val root = determineResourceLocationRoot(file)
        if (root == null) {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for removed file: $file. Cannot process removal.")
            return
        }

        val removedSet = OAnimationSet.ORIGINS.remove(root)
        if (removedSet != null) {
            SparkCore.LOGGER.info("Removed animation set for root '$root' due to file removal: $file. This was a blunt removal.")
            SparkCore.LOGGER.warn("Note: If multiple files contributed to root '$root', their animations are also removed. This might be too aggressive.")
        } else {
            SparkCore.LOGGER.warn("Attempted to remove animations for root '$root' (from file $file), but no set was found for this root.")
        }
        SparkCore.LOGGER.info("Animation removed: $root (entire set). Network sync placeholder.")
    }
}
