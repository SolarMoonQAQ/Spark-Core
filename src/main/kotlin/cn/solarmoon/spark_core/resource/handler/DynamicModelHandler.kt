package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OLocator
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@AutoRegisterHandler
class DynamicModelHandler(
    private val modelRegistry: DynamicAwareRegistry<OModel>
) : IDynamicResourceHandler {
    private val baseModelPath: Path
    private val directoryIdString = "models"

    init {
        val dynamicResourcesRoot = Paths.get("config", "spark_core", "dynamic_resources")
        baseModelPath = dynamicResourcesRoot.resolve(directoryIdString)

        try {
            if (!Files.exists(baseModelPath)) {
                Files.createDirectories(baseModelPath)
                SparkCore.LOGGER.info("Created directory for DynamicModelHandler: {}", baseModelPath)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to create directory for DynamicModelHandler: $baseModelPath", e)
        }

        if (!Files.isDirectory(baseModelPath)) {
            throw IllegalArgumentException("Base path '$baseModelPath' must be a directory.")
        }
        SparkCore.LOGGER.info("DynamicModelHandler initialized for path: {}, using ModelRegistry: {}", baseModelPath, modelRegistry.tagNames)
    }

    override fun getDirectoryPath(): String {
        return baseModelPath.toString()
    }

    override fun getResourceType(): String {
        return "模型"
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseModelPath)) {
            SparkCore.LOGGER.warn("文件 $file 不在基础路径 $baseModelPath 下。无法确定 ResourceLocation。")
            return null
        }

        val relativePath = baseModelPath.relativize(file)
        
        if (relativePath.parent == null) { 
            val namespace = ResourceLocation.DEFAULT_NAMESPACE 
            val pathName = file.nameWithoutExtension.lowercase().replace(" ", "_")
            return ResourceLocation.fromNamespaceAndPath(namespace, pathName)
        } else { 
            val rootDirName = relativePath.getName(0).toString().lowercase().replace(" ", "_")
            val namespace = ResourceLocation.DEFAULT_NAMESPACE 
            return ResourceLocation.fromNamespaceAndPath(namespace, rootDirName)
        }
    }

    private fun processModelData(file: Path, root: ResourceLocation): OModel? {
        try {
            val jsonString = file.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            val fileRootObject = jsonElement.asJsonObject
            val target = fileRootObject.getAsJsonArray("minecraft:geometry").first().asJsonObject.getAsJsonArray("bones")
            val texture = fileRootObject.getAsJsonArray("minecraft:geometry")
                .first().asJsonObject.getAsJsonObject("description")
            val coord =
                Vector2i(GsonHelper.getAsInt(texture, "texture_width"), GsonHelper.getAsInt(texture, "texture_height"))
            val bones = OBone.MAP_CODEC.decode(JsonOps.INSTANCE, target).orThrow.first.mapValues {
                val bone = it.value
                val cubes = bone.cubes.map {
                    OCube(
                        Vec3(-it.originPos.x - it.size.x, it.originPos.y, it.originPos.z).div(16.0),
                        it.size.div(16.0),
                        it.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
                        it.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
                        it.inflate.div(16),
                        it.uvUnion,
                        it.mirror,
                        coord.x,
                        coord.y
                    )
                }.toMutableList()
                val locators =
                    bone.locators.mapValues { (_, value) ->
                        OLocator(
                            value.offset.div(16.0).multiply(-1.0, 1.0, 1.0),
                            value.rotation.multiply(-1.0, -1.0, 1.0).div(16.0)
                        )
                    }
                OBone(
                    bone.name,
                    bone.parentName,
                    bone.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
                    bone.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
                    LinkedHashMap(locators),
                    ArrayList(cubes)
                )
            }
            return OModel(coord.x, coord.y, LinkedHashMap(bones))
        } catch (e: Exception) {
            SparkCore.LOGGER.error("处理模型文件 $file 时出错，根 '$root': {}", e)
            return null
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件添加模型: {}", file)
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定添加文件的 ResourceLocation: {}", file)
            return
        }

        val model = processModelData(file, root)
        if (model != null) {
            OModel.ORIGINS[root] = model
            SparkCore.LOGGER.info("成功添加/更新模型 '{}'", root)
            SparkCore.LOGGER.info("模型已添加/更新: {}. 网络同步占位符。", root)
        } else {
            SparkCore.LOGGER.error("处理并添加模型 '{}' 时失败，来源: {}", root, file)
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件更新模型: {}", file)
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定修改文件的 ResourceLocation: {}", file)
            return
        }
        
        val model = processModelData(file, root)
        if (model != null) {
            OModel.ORIGINS[root] = model 
            SparkCore.LOGGER.info("成功修改/更新模型 '{}'", root)
            SparkCore.LOGGER.info("模型已修改/更新: {}. 网络同步占位符。", root)
        } else {
            SparkCore.LOGGER.error("处理并更新模型 '{}' 时失败，来源: {}", root, file)
        }
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件移除模型: {}", file)
        val root = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定移除文件的 ResourceLocation: {}. 无法处理移除操作。", file)
            return
        }

        val removedModel = OModel.ORIGINS.remove(root)
        if (removedModel != null) {
            SparkCore.LOGGER.info("由于文件移除，已移除模型根 '{}': {}", root, file)
        } else {
            SparkCore.LOGGER.warn("尝试移除模型根 '{}'（来自文件 {}），但未找到与此根对应的模型。", root, file)
        }
        SparkCore.LOGGER.info("模型已移除: {}. 网络同步占位符。", root)
    }

    override fun getDirectoryId(): String {
        return directoryIdString
    }

    override fun getRegistryIdentifier(): ResourceLocation? {
        return modelRegistry.key().location()
    }
}