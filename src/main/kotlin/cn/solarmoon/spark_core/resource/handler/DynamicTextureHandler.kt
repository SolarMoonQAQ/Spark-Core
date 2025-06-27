package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.animation.texture.OTexture
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.util.ResourceExtractionUtil
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.loading.FMLEnvironment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@AutoRegisterHandler
class DynamicTextureHandler(
    internal val textureRegistry: DynamicAwareRegistry<OTexture>
) : IDynamicResourceHandler {
    private val directoryIdString = "textures"
    private var initialScanComplete = false
    
    // 与其他处理器保持一致的路径配置
    private val baseTexturePath: Path = FMLPaths.GAMEDIR.get().resolve("sparkcore").resolve(getDirectoryId())
        .also { path ->
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path)
                    SparkCore.LOGGER.info("Created base directory for DynamicTextureHandler: {}", path)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Failed to create base directory for DynamicTextureHandler {}: {}", path, e.message)
                    throw IllegalStateException("Failed to create base directory: $path", e)
                }
            } else if (!Files.isDirectory(path)) {
                 throw IllegalArgumentException("Base path '$path' exists but is not a directory.")
            }
        }

    init {
        SparkCore.LOGGER.info(
            "DynamicTextureHandler 初始化完成，监控目录ID: {}, 完整路径: {}",
            getDirectoryId(),
            baseTexturePath
        )
    }

    override fun getDirectoryPath(): String {
        return baseTexturePath.toString()
    }

    override fun getResourceType(): String {
        return "纹理"
    }
    
    fun markInitialScanComplete() {
        this.initialScanComplete = true
        SparkCore.LOGGER.info("DynamicTextureHandler (${getResourceType()}) 标记初始扫描完成。后续资源变动将触发同步。")
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseTexturePath)) {
            SparkCore.LOGGER.warn("文件 $file 不在基础路径 $baseTexturePath 下。无法确定 ResourceLocation。")
            return null
        }

        val relativePath = baseTexturePath.relativize(file)
        
        // 构建纹理资源路径，保持文件夹结构
        // 例如: sparkcore/textures/entity/samurai.png -> spark_core:textures/entity/samurai.png
        val pathStr = relativePath.toString().replace("\\", "/")
        return ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "textures/$pathStr")
    }

    private fun loadTextureFromFile(file: Path, location: ResourceLocation): OTexture? {
        return try {
            // 使用NativeImage读取图像文件
            val inputStream = Files.newInputStream(file)
            inputStream.use { stream ->
                val nativeImage = NativeImage.read(stream)
                nativeImage.use { image ->
                    // 将NativeImage转换为ByteArray
                    val textureData = imageToByteArray(image)
                    
                    // 创建OTexture对象
                    val oTexture = OTexture(
                        location = location,
                        textureData = textureData,
                        width = image.width,
                        height = image.height
                    )
                    
                    // 在客户端同时注册到TextureManager (延迟到客户端完全启动后)
                    if (!FMLEnvironment.dist.isDedicatedServer()) {
                        try {
                            val minecraft = Minecraft.getInstance()
                            if (minecraft.textureManager != null) {
                                // 创建新的NativeImage用于DynamicTexture
                                val newNativeImage = NativeImage(image.width, image.height, false)
                                newNativeImage.copyFrom(image)
                                val dynamicTexture = DynamicTexture(newNativeImage)
                                minecraft.textureManager.register(location, dynamicTexture)
                                SparkCore.LOGGER.info("客户端：纹理已注册到TextureManager: {}", location)
                            } else {
                                SparkCore.LOGGER.debug("客户端TextureManager尚未初始化，跳过纹理注册: {}", location)
                            }
                        } catch (e: Exception) {
                            SparkCore.LOGGER.debug("客户端尚未完全初始化，跳过纹理注册: {} - {}", location, e.message)
                        }
                    }
                    
                    SparkCore.LOGGER.info("成功创建OTexture对象: {} 从文件: {}", location, file)
                    oTexture
                }
            }
        } catch (e: IOException) {
            SparkCore.LOGGER.error("加载纹理时发生IO错误: {} 从文件: {}", location, file, e)
            null
        } catch (e: Exception) {
            SparkCore.LOGGER.error("加载纹理时发生未知错误: {} 从文件: {}", location, file, e)
            null
        }
    }

    private fun imageToByteArray(nativeImage: NativeImage): ByteArray {
        // RGBA转换，修复字节顺序问题
        val width = nativeImage.width
        val height = nativeImage.height
        val data = ByteArray(width * height * 4)
        
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = nativeImage.getPixelRGBA(x, y)
                // NativeImage.getPixelRGBA返回ABGR格式，需要正确提取
                data[index++] = (pixel and 0xFF).toByte()        // R
                data[index++] = (pixel shr 8 and 0xFF).toByte()  // G  
                data[index++] = (pixel shr 16 and 0xFF).toByte() // B
                data[index++] = (pixel shr 24 and 0xFF).toByte() // A
            }
        }
        
        return data
    }

    private fun unloadTexture(location: ResourceLocation) {
        try {
            // 客户端：替换为透明纹理而不是完全移除
            if (!FMLEnvironment.dist.isDedicatedServer()) {
                try {
                    val minecraft = Minecraft.getInstance()
                    if (minecraft.textureManager != null) {
                        val textureManager = minecraft.textureManager
                        
                        // 使用Minecraft的缺失纹理作为占位符
                        val missingTextureLocation = MissingTextureAtlasSprite.getLocation()
                        val missingTexture = textureManager.getTexture(missingTextureLocation)
                        
                        // 先release旧纹理
                        textureManager.release(location)
                        // 然后注册缺失纹理作为占位符
                        textureManager.register(location, missingTexture)
                        
                        SparkCore.LOGGER.info("客户端：纹理已替换为缺失纹理占位符(紫黑格子): {}", location)
                    } else {
                        SparkCore.LOGGER.debug("客户端TextureManager尚未初始化，跳过纹理卸载: {}", location)
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.debug("客户端尚未完全初始化，跳过纹理卸载: {} - {}", location, e.message)
                }
            }
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("卸载纹理时发生错误: {}", location, e)
        }
    }

    override fun onResourceAdded(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件添加纹理: {}", file)
        
        // 只处理图像文件
        val fileName = file.fileName.toString().lowercase()
        if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
            SparkCore.LOGGER.debug("跳过非图像文件: {}", file)
            return
        }
        
        val location = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定添加文件的 ResourceLocation: {}", file)
            return
        }

        val oTexture = loadTextureFromFile(file, location)
        if (oTexture != null) {
            // 注册到动态注册表，触发网络同步
            if (initialScanComplete) {
                textureRegistry.registerDynamic(location, oTexture)
                SparkCore.LOGGER.info("成功注册纹理到动态注册表: {}", location)
            } else {
                // 初始扫描阶段，只本地注册，不触发网络同步
                val resourceKey = net.minecraft.resources.ResourceKey.create(textureRegistry.key(), location)
                textureRegistry.register(resourceKey, oTexture, net.minecraft.core.RegistrationInfo.BUILT_IN)
                SparkCore.LOGGER.debug("初始扫描：纹理已本地注册: {}", location)
            }
        } else {
            SparkCore.LOGGER.error("添加纹理 '{}' 时失败，来源: {}", location, file)
        }
    }

    override fun onResourceModified(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件更新纹理: {}", file)
        
        // 只处理图像文件
        val fileName = file.fileName.toString().lowercase()
        if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
            SparkCore.LOGGER.debug("跳过非图像文件: {}", file)
            return
        }
        
        val location = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定修改文件的 ResourceLocation: {}", file)
            return
        }
        
        val oTexture = loadTextureFromFile(file, location)
        if (oTexture != null) {
            // 更新动态注册表，触发网络同步
            if (initialScanComplete) {
                textureRegistry.registerDynamic(location, oTexture)
                SparkCore.LOGGER.info("成功更新纹理到动态注册表: {}", location)
            } else {
                // 初始扫描阶段，只本地注册
                val resourceKey = net.minecraft.resources.ResourceKey.create(textureRegistry.key(), location)
                textureRegistry.register(resourceKey, oTexture, net.minecraft.core.RegistrationInfo.BUILT_IN)
                SparkCore.LOGGER.debug("初始扫描：纹理已更新: {}", location)
            }
        } else {
            SparkCore.LOGGER.error("修改/更新纹理 '{}' 时失败，来源: {}", location, file)
        }
    }

    override fun onResourceRemoved(file: Path) {
        SparkCore.LOGGER.debug("尝试从文件移除纹理: {}", file)
        val location = determineResourceLocationRoot(file) ?: run {
            SparkCore.LOGGER.error("无法确定移除文件的 ResourceLocation: {}. 无法处理移除操作。", file)
            return
        }

        // 从动态注册表移除，触发网络同步
        if (initialScanComplete) {
            textureRegistry.unregisterDynamic(location)
            SparkCore.LOGGER.info("从动态注册表移除纹理: {}", location)
        } else {
            // 初始扫描阶段，只本地移除
            textureRegistry.unregisterDynamic(location)
            SparkCore.LOGGER.debug("初始扫描：纹理已移除: {}", location)
        }
        
        // 本地纹理清理
        unloadTexture(location)
    }

    override fun getDirectoryId(): String {
        return directoryIdString
    }

    override fun getRegistryIdentifier(): ResourceLocation? {
        return null // 现在使用动态注册表
    }

    override fun getSourceResourceDirectoryName(): String = "textures"

    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val targetRuntimeBaseDir = File(gameDir, "sparkcore")
        val success = ResourceExtractionUtil.extractResourcesFromJar(
            modMainClass,
            getSourceResourceDirectoryName(),      // "textures"
            targetRuntimeBaseDir,
            getDirectoryId(),                      // "textures"
            SparkCore.LOGGER
        )
        
        // 如果提取成功，处理所有提取的图像文件
        if (success) {
            val finalTargetDir = File(targetRuntimeBaseDir, getDirectoryId())
            for (file in finalTargetDir.walk()) {
                if (file.isFile) {
                    val fileName = file.name.lowercase()
                    if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        onResourceAdded(file.toPath())
                    }
                }
            }
        }
        
        SparkCore.LOGGER.info("纹理默认资源初始化完成: {}", if (success) "成功" else "失败")
        return success
    }
} 