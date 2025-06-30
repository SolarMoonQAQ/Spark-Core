package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.ik.origin.OIKConstraint
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@AutoRegisterHandler
class DynamicIKConstraintHandler(
    private val ikComponentRegistry: DynamicAwareRegistry<TypedIKComponent>
) : IDynamicResourceHandler {
    private val baseIKPath: Path
    private val directoryIdString = "ik_constraints"

    init {
        val dynamicResourcesRoot = Paths.get("config", "spark_core", "dynamic_resources")
        baseIKPath = dynamicResourcesRoot.resolve(directoryIdString)

        try {
            if (!Files.exists(baseIKPath)) {
                Files.createDirectories(baseIKPath)
                SparkCore.LOGGER.info("Created directory for DynamicIKConstraintHandler: {}", baseIKPath)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to create directory for DynamicIKConstraintHandler: $baseIKPath.${e.message}")
        }

        if (!Files.isDirectory(baseIKPath)) {
            throw IllegalArgumentException("Base path '{}' must be a directory.", )
        }
        SparkCore.LOGGER.info("DynamicIKConstraintHandler initialized for path: {}, using IKComponentRegistry: {}", baseIKPath, ikComponentRegistry.key().location().toString())
    }

    override fun getDirectoryPath(): String {
        return baseIKPath.toString()
    }

    override fun getResourceType(): String {
        return "ik_constraint"
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseIKPath)) {
            SparkCore.LOGGER.warn("File {} is not under the base path {}, unable to determine ResourceLocation.", file, baseIKPath)
            return null
        }

        val relativePath = baseIKPath.relativize(file)
        
        // IK 约束通常是平铺在目录中的，例如 geo/ik_constraints/player_ik.json
        // 所以，文件名（不包括扩展名）将成为 ResourceLocation 的路径。
        if (relativePath.parent == null) { 
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // 或者是你的模组命名空间
            val pathName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            return ResourceLocation.fromNamespaceAndPath(namespace, pathName)
        } else { 
            // 基于 IKConstraintListener.kt 的结构，这种情况理论上不应该发生。
            // 如果发生了，我们将使用第一个目录组件作为根。
            SparkCore.LOGGER.warn("File {} is in a subdirectory {} of the base path {}, this is unusual for IK constraints. Using the first directory as the root.", file, relativePath.parent, baseIKPath)
            val rootDirName = relativePath.getName(0).toString().lowercase().replace(" ", "_")
            val namespace = ResourceLocation.DEFAULT_NAMESPACE 
            return ResourceLocation.fromNamespaceAndPath(namespace, rootDirName)
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("Attempting to add IK constraint from file: {}", file)
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Unable to determine ResourceLocation for added file: {}", file)
            return
        }

        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            
            if (!jsonElement.isJsonArray) {
                SparkCore.LOGGER.error("IK constraint file {} for root '{}' is not a JSON array, skipping.", file, root)
                return
            }
            val jsonArray = jsonElement.asJsonArray
            if (jsonArray.isEmpty) {
                 SparkCore.LOGGER.warn("IK constraint file {} for root '{}' is an empty JSON array, no constraints to load.", file, root)
                return
            }

            var constraintCount = 0
            jsonArray.forEach { constraintJsonElement ->
                val parsedConstraint = OIKConstraint.CODEC.decode(JsonOps.INSTANCE, constraintJsonElement).orThrow.first
                
                // 这将覆盖来自同一文件的同一根的先前约束。
                OIKConstraint.ORIGINS[root] = parsedConstraint
                constraintCount++
            }

            if (constraintCount > 0) {
                SparkCore.LOGGER.info("Successfully loaded {} IK constraints from file: {}", constraintCount, file)
                if (constraintCount > 1) {
                    SparkCore.LOGGER.warn("File {} contains {} IK constraints for root '{}', only the last constraint in the JSON array is stored in OIKConstraint.ORIGINS for this root.", file, constraintCount, root)
                }
                SparkCore.LOGGER.info("IK constraint added/updated: {}. Network sync placeholder.", root)
            } else {
                 SparkCore.LOGGER.warn("No IK constraints successfully parsed from file: {} for root '{}'.", file, root)
            }

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Error processing IK constraint file (add {}): {}", file, e.message, e)
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("Attempting to update IK constraint from file: {}", file)
        // Modification handling is the same as addition: re-read and replace.
        onResourceAdded(file) 
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("Attempting to remove IK constraint from file: {}", file)
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Unable to determine ResourceLocation for removed file: {}. Unable to process removal.", file)
            return
        }

        val removedConstraint = OIKConstraint.ORIGINS.remove(root)
        if (removedConstraint != null) {
            SparkCore.LOGGER.info("IK constraint removed: {} due to file removal: {}", root, file)
        } else {
            SparkCore.LOGGER.warn("Attempted to remove IK constraint {} but it was not found.", root)
        }
        SparkCore.LOGGER.info("IK constraint removed: {}. Network sync placeholder.", root)
    }

    override fun getDirectoryId(): String {
        return directoryIdString
    }

    override fun getRegistryIdentifier(): ResourceLocation? {
        return ikComponentRegistry.key().location()
    }
}