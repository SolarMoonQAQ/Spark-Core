package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import cn.solarmoon.spark_core.animation.model.origin.OLocator
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.sync.OAnimationSetSyncPayload
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.common.*
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector2i
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * 重构后的模型处理器
 * 使用统一的接口和服务，标准化路径解析和资源管理
 */
@AutoRegisterHandler
class ModelHandler(
    private val modelRegistry: DynamicAwareRegistry<OModel>
) : ResourceHandlerBase() {

    companion object {
        init {
            HandlerDiscoveryService.registerHandler {
                ModelHandler(cn.solarmoon.spark_core.registry.common.SparkRegistries.MODELS)
            }
        }
    }

    private val resourceType = "models"
    private val supportedExtensions = setOf("json")
    private var processedCount = 0

    init {
        SparkCore.LOGGER.info("ModelHandler 初始化完成")
    }
    
    // ===== 基础接口实现 =====
    
    override fun getResourceType(): String = resourceType
    
    override fun getRegistryIdentifier(): ResourceLocation? = modelRegistry.key().location()
    
    override fun getSupportedExtensions(): Set<String> = supportedExtensions
    
    override fun getPriority(): Int = 20 // 中等优先级
    
    // 提供对注册表的访问 (for DynamicResourceApplier)
    val modelRegistryAccess: DynamicAwareRegistry<OModel>
        get() = this.modelRegistry
    
    // ===== 资源处理核心逻辑 =====
    
    override fun processResourceAdded(node: ResourceNode) {
        try {
            val model = parseModelFile(node.basePath.resolve(node.relativePath))
            
            // 存储到Origin映射
            OModel.ORIGINS[node.id] = model
            
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 添加到模块资源映射
            addResourceToModule(moduleId, node.id)

            // 增加处理计数
            processedCount++

            SparkCore.LOGGER.debug("处理模型资源: ${node.id} (模块: $moduleId)")
            registerToRegistry(node.id, model)
            // 如果初始扫描完成，注册到动态注册表
            if (isInitialScanComplete()) {
                // TODO: 模型增量同步包
            }
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }
    
    override fun processResourceModified(node: ResourceNode) {
        // 修改等同于重新添加
        processResourceAdded(node)
    }
    
    override fun processResourceRemoved(node: ResourceNode) {
        val resourceId = node.id
        val removed = OModel.ORIGINS.remove(resourceId)
        if (removed != null) {
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 从模块资源映射中移除
            removeResourceFromModule(moduleId, resourceId)
            
            if (isInitialScanComplete()) {
                unregisterFromRegistry(resourceId)
            }
        } else {
            SparkCore.LOGGER.warn("尝试移除不存在的模型: $resourceId")
        }
    }
    
    // ===== 模型解析 =====
    
    private fun parseModelFile(filePath: Path): OModel {
        try {
            val jsonString = filePath.readText()
            val jsonElement = JsonParser.parseString(jsonString)
            val fileRootObject = jsonElement.asJsonObject
            
            // 解析几何体数据
            val geometryArray = fileRootObject.getAsJsonArray("minecraft:geometry")
            if (geometryArray.isEmpty) {
                throw IllegalArgumentException("模型文件缺少几何体数据")
            }
            
            val geometryObject = geometryArray.first().asJsonObject
            val bonesArray = geometryObject.getAsJsonArray("bones")
            val description = geometryObject.getAsJsonObject("description")
            
            // 解析纹理坐标
            val textureWidth = GsonHelper.getAsInt(description, "texture_width", 64)
            val textureHeight = GsonHelper.getAsInt(description, "texture_height", 64)
            val textureCoord = Vector2i(textureWidth, textureHeight)
            
            // 解析骨骼数据
            val bonesResult = OBone.MAP_CODEC.decode(JsonOps.INSTANCE, bonesArray).orThrow
            val bones = bonesResult.first.mapValues { (_, bone) ->
                processBone(bone, textureCoord)
            }
            
            return OModel(textureCoord.x, textureCoord.y, LinkedHashMap(bones))
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(filePath.toString(), e)
        }
    }
    
    private fun processBone(bone: OBone, textureCoord: Vector2i): OBone {
        // 处理立方体
        val cubes = bone.cubes.map { cube ->
            OCube(
                Vec3(-cube.originPos.x - cube.size.x, cube.originPos.y, cube.originPos.z).div(16.0),
                cube.size.div(16.0),
                cube.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
                cube.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
                cube.inflate.div(16),
                cube.uvUnion,
                cube.mirror,
                textureCoord.x,
                textureCoord.y
            )
        }.toMutableList()
        
        // 处理定位器
        val locators = bone.locators.mapValues { (_, locator) ->
            OLocator(
                locator.offset.div(16.0).multiply(-1.0, 1.0, 1.0),
                locator.rotation.multiply(-1.0, -1.0, 1.0).div(16.0)
            )
        }
        
        return OBone(
            bone.name,
            bone.parentName,
            bone.pivot.multiply(-1.0, 1.0, 1.0).div(16.0),
            bone.rotation.multiply(-1.0, -1.0, 1.0).toRadians(),
            LinkedHashMap(locators),
            ArrayList(cubes)
        )
    }
    
    // ===== 注册表操作 =====
    
    private fun registerToRegistry(location: ResourceLocation, model: OModel) {
        try {
            val resourceKey = ResourceKey.create(modelRegistry.key(), location)
            modelRegistry.register(resourceKey, model, RegistrationInfo.BUILT_IN)
            
            SparkCore.LOGGER.debug("模型已注册到注册表: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("REGISTER", location.toString(), e)
        }
    }
    
    private fun unregisterFromRegistry(location: ResourceLocation) {
        try {
            modelRegistry.unregisterDynamic(location)
            
            SparkCore.LOGGER.debug("模型已从注册表注销: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("UNREGISTER", location.toString(), e)
        }
    }

    
    // ===== 初始化 =====
    
    override fun initialize(modMainClass: Class<*>):Boolean {
        return try {
            // 发现资源路径
            val resourcePaths = ResourceDiscoveryService.discoverResourcePaths(resourceType)
            
            // 提取默认资源
            val extractionSuccess = extractDefaultResources(modMainClass)
            
            // 扫描并处理现有资源
            for (basePath in resourcePaths) {
                val resourceFiles = ResourceDiscoveryService.scanResourceFiles(basePath, supportedExtensions)
                
                for (resourceFile in resourceFiles) {
                    onResourceAdded(resourceFile)
                }
            }
            
            SparkCore.LOGGER.info("ModelHandler 初始化完成，处理了 $processedCount 个模型")
            extractionSuccess
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("ModelHandler 初始化失败", e)
            false
        }
    }
    
    private fun extractDefaultResources(modMainClass: Class<*>): Boolean {
        return MultiModuleResourceExtractionUtil.extractAllModuleResources(
            modMainClass,
            resourceType
        )
    }
    
    // ===== 模块感知扩展 =====
    
    override fun unregisterModule(moduleId: String) {
        try {
            val resources = moduleResources[moduleId] ?: emptySet()
            
            for (resourceLocation in resources) {
                // 从Origins中移除
                OModel.ORIGINS.remove(resourceLocation)
                
                // 从注册表中移除
                if (isInitialScanComplete()) {
                    unregisterFromRegistry(resourceLocation)
                }
            }
            
            super.unregisterModule(moduleId)
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ModuleOperationException(moduleId, "UNREGISTER", e)
        }
    }
    
    override fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 清理模型资源
        OModel.ORIGINS.remove(resourceLocation)
        
        // 如果初始扫描完成，从注册表中移除
        if (isInitialScanComplete()) {
            unregisterFromRegistry(resourceLocation)
        }
    }
}