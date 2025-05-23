package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OLocator
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.toRadians
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class DynamicModelHandler(private val baseModelPath: Path) : IDynamicResourceHandler {

    init {
        if (!Files.isDirectory(baseModelPath)) {
            throw IllegalArgumentException("Base path '$baseModelPath' must be a directory and exist.")
        }
        SparkCore.LOGGER.info("DynamicModelHandler initialized for path: $baseModelPath")
    }

    override fun getDirectoryPath(): String {
        return baseModelPath.toString()
    }

    override fun getResourceType(): String {
        return "model"
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseModelPath)) {
            SparkCore.LOGGER.warn("File $file is not under base path $baseModelPath. Cannot determine ResourceLocation.")
            return null
        }

        val relativePath = baseModelPath.relativize(file)
        
        if (relativePath.parent == null) { // File is directly under baseModelPath, e.g., base/custom_mob.json
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // Or your mod's namespace
            val pathName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            return ResourceLocation.fromNamespaceAndPath(namespace, pathName)
        } else { // File is in a subdirectory, e.g., base/player/walk.json
            val rootDirName = relativePath.getName(0).toString().lowercase().replace(" ", "_")
            val namespace = ResourceLocation.DEFAULT_NAMESPACE // Or your mod's namespace
            return ResourceLocation.fromNamespaceAndPath(namespace, rootDirName)
        }
    }

    private fun processModelData(file: Path, root: ResourceLocation): OModel? {
        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            val fileRootObject = jsonElement.asJsonObject

            // Mimic EntityModelListener: geometry is an array, take the first element.
            val geoNode = GsonHelper.getAsJsonArray(fileRootObject, "minecraft:geometry")[0].asJsonObject
            val descriptionNode = GsonHelper.getAsJsonObject(geoNode, "description")
            val textureWidth = GsonHelper.getAsInt(descriptionNode, "texture_width")
            val textureHeight = GsonHelper.getAsInt(descriptionNode, "texture_height")
            
            val bonesArray = GsonHelper.getAsJsonArray(geoNode, "bones")
            val processedBones = LinkedHashMap<String, OBone>()

            for (boneElement in bonesArray) {
                val boneJson = boneElement.asJsonObject
                // Decode initial bone structure
                val rawBone = OBone.CODEC.decode(JsonOps.INSTANCE, boneJson).orThrow.first

                val processedCubes = mutableListOf<OCube>()
                rawBone.cubes.forEach { cube ->
                    val newOrigin = Vec3(-cube.originPos.x - cube.size.x, cube.originPos.y, cube.originPos.z).div(16.0)
                    val newSize = cube.size.div(16.0)
                    // val newPivot = Vec3(-cube.pivot.x, cube.pivot.y, cube.pivot.z).div(16.0) // Corrected pivot Y
                    val newPivot = Vec3(-cube.pivot.x, -cube.pivot.y, cube.pivot.z).div(16.0) // EntityModelListener: pivot.mul(-1.0F, -1.0F, 1.0F)
                    val newRotation = Vec3(cube.rotation.x, -cube.rotation.y, -cube.rotation.z).toRadians()

                    processedCubes.add(
                        OCube(
                            newOrigin, newSize, newPivot, newRotation,
                            cube.inflate, cube.uvUnion, cube.mirror,
                            textureWidth, textureHeight // Pass texture dimensions
                        )
                    )
                }

                val processedLocators = LinkedHashMap<String, OLocator>()
                rawBone.locators.forEach { (name, locator) ->
                    // val newOffset = Vec3(-locator.offset.x, locator.offset.y, locator.offset.z).div(16.0) // Corrected offset Y
                    val newOffset = Vec3(-locator.offset.x, locator.offset.y, locator.offset.z).div(16.0) // EntityModelListener: offset.mul(-1.0F, 1.0F, 1.0F)
                    val newRotation = Vec3(locator.rotation.x, -locator.rotation.y, -locator.rotation.z).toRadians()
                    processedLocators[name] = OLocator(newOffset, newRotation)
                }

                // Recreate bone with processed components
                processedBones.put(rawBone.name, OBone(rawBone.name, rawBone.getParent()?.name,rawBone.pivot,  rawBone.rotation, processedLocators, processedCubes))
            }
            
            return OModel(textureWidth, textureHeight, processedBones)

        } catch (e: Exception) {
            SparkCore.LOGGER.error("Error processing model file $file for root '$root': ${e.message}", e)
            return null
        }
    }


    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("Attempting to add model from file: $file")
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for added file: $file")
            return
        }

        val model = processModelData(file, root)
        if (model != null) {
            OModel.ORIGINS[root] = model
            SparkCore.LOGGER.info("Successfully added/updated model for '$root' from $file.")
            SparkCore.LOGGER.info("Model added/updated: $root. Network sync placeholder.")
        } else {
            SparkCore.LOGGER.error("Failed to process and add model for '$root' from $file.")
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("Attempting to update model from file: $file")
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for modified file: $file")
            return
        }
        
        // Re-processing the file is equivalent to an update.
        val model = processModelData(file, root)
        if (model != null) {
            OModel.ORIGINS[root] = model // This overwrites the existing entry, effectively an update.
            SparkCore.LOGGER.info("Successfully modified/updated model for '$root' from $file.")
            SparkCore.LOGGER.info("Model modified/updated: $root. Network sync placeholder.")
        } else {
            SparkCore.LOGGER.error("Failed to process and update model for '$root' from $file.")
        }
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("Attempting to remove model from file: $file")
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("Could not determine ResourceLocation for removed file: $file. Cannot process removal.")
            return
        }

        val removedModel = OModel.ORIGINS.remove(root)
        if (removedModel != null) {
            SparkCore.LOGGER.info("Removed model for root '$root' due to file removal: $file.")
        } else {
            SparkCore.LOGGER.warn("Attempted to remove model for root '$root' (from file $file), but no model was found for this root.")
        }
        SparkCore.LOGGER.info("Model removed: $root. Network sync placeholder.")
    }
}
