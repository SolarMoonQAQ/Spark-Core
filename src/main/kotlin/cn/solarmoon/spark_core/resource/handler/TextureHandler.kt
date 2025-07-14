package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.common.*
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.util.MultiModuleResourceExtractionUtil
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.RegistrationInfo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * 重构后的纹理处理器
 * 使用统一的接口和服务，标准化路径解析和资源管理
 */
@AutoRegisterHandler
class TextureHandler(
    private val textureRegistry: DynamicAwareRegistry<OTexture>
) : ResourceHandlerBase() {
    
    private val resourceType = "textures"
    private val supportedExtensions = setOf("png", "jpg", "jpeg", "tga", "bmp")
    private var processedCount = 0
    
    init {
        SparkCore.LOGGER.info("TextureHandler 初始化完成")
        // 注意：移除对DependencyGraph的注册，使用MetadataManager进行依赖管理
    }
    
    // ===== 基础接口实现 =====
    
    override fun getResourceType(): String = resourceType
    
    override fun getRegistryIdentifier(): ResourceLocation? = textureRegistry.key().location()
    
    override fun getSupportedExtensions(): Set<String> = supportedExtensions
    
    override fun getPriority(): Int = 50 // 较低优先级，纹理通常在其他资源之后处理
    
    // 提供对注册表的访问 (for DynamicResourceApplier)
    val textureRegistryAccess: DynamicAwareRegistry<OTexture>
        get() = this.textureRegistry
    
    // ===== 资源处理核心逻辑 =====
    
    override fun processResourceAdded(node: ResourceNode) {
        try {
            val texture = loadTextureFromFile(node.basePath.resolve(node.relativePath), node.id)
            
            // 存储到Origin映射
            OTexture.ORIGINS[node.id] = texture
            
            // 增加处理计数
            processedCount++

            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 添加到模块资源映射
            addResourceToModule(moduleId, node.id)

            SparkCore.LOGGER.debug("处理纹理资源: ${node.id} (模块: $moduleId)")
            registerToRegistry(node.id, texture)
            // 如果初始扫描完成，注册到动态注册表
            if (isInitialScanComplete()) {
                // 在客户端注册纹理
                if (FMLEnvironment.dist.isClient) {
                    registerClientTexture(node.id, texture)
                }
            }
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }
    
    override fun processResourceModified(node: ResourceNode) {
        try {
            // 先移除旧纹理
            val oldTexture = OTexture.ORIGINS[node.id]
            if (oldTexture != null && FMLEnvironment.dist.isClient) {
                unregisterClientTexture(node.id)
            }
            
            // 重新添加
            processResourceAdded(node)
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(node.id.toString(), e)
        }
    }
    
    override fun processResourceRemoved(node: ResourceNode) {
        val resourceId = node.id
        val removed = OTexture.ORIGINS.remove(resourceId)
        if (removed != null) {
            // 使用完整的模块标识（modId:moduleName格式）
            val moduleId = node.getFullModuleId()

            // 从模块资源映射中移除
            removeResourceFromModule(moduleId, resourceId)
            
            if (isInitialScanComplete()) {
                unregisterFromRegistry(resourceId)
                
                // 在客户端注销纹理
                if (FMLEnvironment.dist.isClient) {
                    unregisterClientTexture(resourceId)
                }
            }
        } else {
            SparkCore.LOGGER.warn("尝试移除不存在的纹理: $resourceId")
        }
    }
    
    // ===== 纹理加载 =====
    
    private fun loadTextureFromFile(filePath: Path, location: ResourceLocation): OTexture {
        try {
            if (!Files.exists(filePath)) {
                throw IOException("纹理文件不存在: $filePath")
            }
            
            val fileSize = Files.size(filePath)
            val imageFormat = getImageFormat(filePath)
            
            // 读取图像获取实际尺寸
            val bufferedImage = ImageIO.read(filePath.toFile())
            val actualWidth = bufferedImage?.width ?: 0
            val actualHeight = bufferedImage?.height ?: 0
            
            // 创建纹理数据
            val textureData = if (bufferedImage != null) {
                val width = bufferedImage.width
                val height = bufferedImage.height
                val data = ByteArray(width * height * 4)
                
                var index = 0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val argb = bufferedImage.getRGB(x, y)
                        // BufferedImage.getRGB返回ARGB格式，需要正确提取各通道
                        data[index++] = (argb shr 16 and 0xFF).toByte() // R
                        data[index++] = (argb shr 8 and 0xFF).toByte()  // G
                        data[index++] = (argb and 0xFF).toByte()        // B
                        data[index++] = (argb shr 24 and 0xFF).toByte() // A
                    }
                }
                data
            } else {
                ByteArray(0)
            }
            
            // 创建纹理对象
            return OTexture(
                location = location,
                textureData = textureData,
                width = actualWidth,
                height = actualHeight
            )
            
        } catch (e: Exception) {
            throw ResourceHandlerException.ResourceParseException(filePath.toString(), e)
        }
    }
    
    private fun getImageFormat(filePath: Path): String {
        val extension = filePath.fileName.toString().substringAfterLast('.', "").lowercase()
        return when (extension) {
            "png" -> "PNG"
            "jpg", "jpeg" -> "JPEG"
            "tga" -> "TGA"
            "bmp" -> "BMP"
            else -> "PNG" // 默认格式
        }
    }
    
    // ===== 客户端纹理管理 =====
    
    private fun registerClientTexture(location: ResourceLocation, texture: OTexture) {
        try {
            if (!FMLEnvironment.dist.isClient) return
            
            // 在客户端线程中注册纹理
            Minecraft.getInstance().execute {
                try {
                    val textureManager = Minecraft.getInstance().textureManager

                    // 使用现有的纹理数据，不需要文件路径
                    val nativeImage = NativeImage(texture.width, texture.height, false)
                    // 将纹理数据复制到NativeImage
                    texture.textureData.indices.step(4).forEach { i ->
                        val x = (i / 4) % texture.width
                        val y = (i / 4) / texture.width
                        if (y < texture.height) {
                            val r = texture.textureData[i].toInt() and 0xFF
                            val g = texture.textureData[i + 1].toInt() and 0xFF
                            val b = texture.textureData[i + 2].toInt() and 0xFF
                            val a = texture.textureData[i + 3].toInt() and 0xFF
                            // NativeImage.setPixelRGBA需要ABGR格式的像素值
                            val pixel = (a shl 24) or (r shl 16) or (g shl 8) or b
                            nativeImage.setPixelRGBA(x, y, pixel)
                        }
                    }
                    val dynamicTexture = DynamicTexture(nativeImage)
                    textureManager.register(location, dynamicTexture)
                    SparkCore.LOGGER.debug("客户端纹理已注册: $location")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("注册客户端纹理失败: $location", e)
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("客户端纹理注册失败: $location", e)
        }
    }
    
    private fun unregisterClientTexture(location: ResourceLocation) {
        try {
            if (!FMLEnvironment.dist.isClient) return
            
            // 在客户端线程中注销纹理
            Minecraft.getInstance().execute {
                try {
                    val textureManager = Minecraft.getInstance().textureManager
                    textureManager.release(location)
                    SparkCore.LOGGER.debug("客户端纹理已注销: $location")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("注销客户端纹理失败: $location", e)
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("客户端纹理注销失败: $location", e)
        }
    }
    
    // ===== 注册表操作 =====
    
    private fun registerToRegistry(location: ResourceLocation, texture: OTexture) {
        try {
            val resourceKey = ResourceKey.create(textureRegistry.key(), location)
            textureRegistry.register(resourceKey, texture, RegistrationInfo.BUILT_IN)
            
            SparkCore.LOGGER.debug("纹理已注册到注册表: $location")
        } catch (e: Exception) {
            throw ResourceHandlerException.RegistryOperationException("REGISTER", location.toString(), e)
        }
    }
    
    private fun unregisterFromRegistry(location: ResourceLocation) {
        try {
            val resourceKey = ResourceKey.create(textureRegistry.key(), location)
            textureRegistry.unregisterDynamic(resourceKey)
            
            SparkCore.LOGGER.debug("纹理已从注册表注销: $location")
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
            
            SparkCore.LOGGER.info("TextureHandler 初始化完成，处理了 $processedCount 个纹理")
            extractionSuccess
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("TextureHandler 初始化失败", e)
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
                OTexture.ORIGINS.remove(resourceLocation)
                
                // 注销客户端纹理
                if (FMLEnvironment.dist.isClient) {
                    unregisterClientTexture(resourceLocation)
                }
                
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
        // 清理纹理资源
        OTexture.ORIGINS.remove(resourceLocation)
        
        // 注销客户端纹理
        if (FMLEnvironment.dist.isClient) {
            unregisterClientTexture(resourceLocation)
        }
        
        // 如果初始扫描完成，从注册表中移除
        if (isInitialScanComplete()) {
            unregisterFromRegistry(resourceLocation)
        }
    }
}