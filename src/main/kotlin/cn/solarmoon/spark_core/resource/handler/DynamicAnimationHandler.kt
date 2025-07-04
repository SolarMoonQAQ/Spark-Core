package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.sync.OAnimationSetSyncPayload
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.util.ResourceExtractionUtil
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@AutoRegisterHandler
class DynamicAnimationHandler(
    internal val typedAnimationRegistry: DynamicAwareRegistry<TypedAnimation>
) : IDynamicResourceHandler {

    private var initialScanComplete = false

    fun markInitialScanComplete() {
        this.initialScanComplete = true
        SparkCore.LOGGER.info("DynamicAnimationHandler (${getResourceType()}) 标记初始扫描完成。后续资源变动将触发同步。")
    }

    override fun getResourceType(): String = "动画"
    override fun getDirectoryId(): String = "animations"
    override fun getRegistryIdentifier(): ResourceLocation? {
        return typedAnimationRegistry.key().location()
    }

    private val baseAnimationPath: Path = FMLPaths.GAMEDIR.get().resolve("sparkcore").resolve(getDirectoryId())
        .also { path ->
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path)
                    SparkCore.LOGGER.info("Created base directory for DynamicAnimationHandler: {}", path)
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("Failed to create base directory for DynamicAnimationHandler {}: {}", path, e.message)
                    throw IllegalStateException("Failed to create base directory: $path", e)
                }
            } else if (!Files.isDirectory(path)) {
                 throw IllegalArgumentException("Base path '$path' exists but is not a directory.")
            }
        }

    private val fileToRegisteredAnimationKeys: MutableMap<Path, MutableList<ResourceKey<TypedAnimation>>> = mutableMapOf()
    private val typedAnimationRegistryKey: ResourceKey<out Registry<TypedAnimation>> = typedAnimationRegistry.key()

    init {
        SparkCore.LOGGER.info(
            "DynamicAnimationHandler 初始化完成，监控目录ID: {}, 完整路径: {}, 动画注册表: {}",
            getDirectoryId(),
            baseAnimationPath,
            typedAnimationRegistryKey.location()
        )
    }

    override fun getDirectoryPath(): String {
        return baseAnimationPath.toString()
    }

    override fun getSourceResourceDirectoryName(): String = "animations"

    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        val gameDir = FMLPaths.GAMEDIR.get().toFile()
        val targetRuntimeBaseDir = File(gameDir, "sparkcore")
        val success = ResourceExtractionUtil.extractResourcesFromJar(
            modMainClass,
            "assets/spark_core/" + getSourceResourceDirectoryName(),      // should be "animations"
            targetRuntimeBaseDir,
            getDirectoryId(),                      // should be "animations"
            SparkCore.LOGGER
        )
        // 递归遍历targetRuntimeBaseDir, 处理所有文件
        val finalTargetDir = File(targetRuntimeBaseDir, getDirectoryId())
        if (success) {
            for (file in finalTargetDir.walk()) {
                if (file.isFile) {
                    processFile(file.toPath(), isDeletion = false)
                }
            }
        }
        return success
    }

    private fun determineResourceLocationRoot(file: Path): ResourceLocation? {
        if (!file.startsWith(baseAnimationPath)) {
            SparkCore.LOGGER.warn("文件 $file 不在基础路径 $baseAnimationPath 下。无法确定 ResourceLocation。")
            return null
        }

        val relativePath = baseAnimationPath.relativize(file)

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

    override fun onResourceAdded(file: Path) {
        processFile(file, isDeletion = false)
    }

    override fun onResourceModified(file: Path) {
        processFile(file, isDeletion = false)
    }

    override fun onResourceRemoved(file: Path) {
        processFile(file, isDeletion = true)
    }

    private fun processFile(filePath: Path, isDeletion: Boolean, syncToPlayers: Boolean = true) {
        val fileName = filePath.fileName.toString()
        if (!fileName.endsWith(".json")) {
            SparkCore.LOGGER.debug("文件 {} 不是json，已跳过动画处理。", filePath)
            return
        }

        // 从文件路径构造 ResourceLocation, 例如 assets/sparkcore_animations/animations/test/my_anim.json -> sparkcore_animations:test/my_anim
        // 注意：这里的逻辑假设 'animations' 是根目录，其下的结构代表 ResourceLocation 的 path
        // 例如 sparkcore/animations/test/subdir/my_animation.json -> <animationNamespace>:test/subdir/my_animation
        val relativePath = Path.of(filePath.parent.parent.toString()).relativize(filePath.parent)
        val rootLocation = ResourceLocation.withDefaultNamespace(relativePath.toString())

        if (isDeletion) {
            // 对于删除操作，我们只需要 rootLocation 来从 ORIGINS 中移除，并通知客户端
            // animationSet 将是一个空的 OAnimationSet
            processParsedResource(rootLocation, OAnimationSet(LinkedHashMap()), filePath, isDeletion = true, syncToPlayers = syncToPlayers)
            return
        }

        // 对于添加或修改，需要读取和解析文件内容
        try {
            val content = filePath.toFile().readText(Charsets.UTF_8)
            val jsonObject = JsonParser.parseString(content)
            val newOAnimationSetFromFile = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, jsonObject).orThrow.first

            processParsedResource(rootLocation, newOAnimationSetFromFile, filePath, isDeletion = false, syncToPlayers = syncToPlayers)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("处理动画文件 {} 失败: {}", filePath, e.message, e)
        }
    }

    private fun processParsedResource(
        rootLocation: ResourceLocation,
        animationSet: OAnimationSet, // 对于删除，这会是一个空的 OAnimationSet
        filePath: Path, // 主要用于日志
        isDeletion: Boolean,
        syncToPlayers: Boolean
    ) {
        val logOperation = if (isDeletion) "移除" else "添加/更新"
        SparkCore.LOGGER.info("服务器端开始 $logOperation OAnimationSet: '$rootLocation' (文件: $filePath)")

        val finalAnimationSetToSync: OAnimationSet? // 用于网络同步的集合，删除时是空集合

        if (isDeletion) {
            OAnimationSet.ORIGINS.remove(rootLocation)
            finalAnimationSetToSync = animationSet // 传入的空 OAnimationSet
            SparkCore.LOGGER.info("已从服务器端 OAnimationSet.ORIGINS 中移除 '$rootLocation'。")
        } else {
            val updatedSet = OAnimationSet.ORIGINS.compute(rootLocation) { _, existingSet ->
                // 如果现有的 set 为空，则使用新的 animationSet
                // 否则，将新的 animationSet 中的动画合并到现有的 set 中
                existingSet?.apply { this.animations.putAll(animationSet.animations) } ?: animationSet
            }
            finalAnimationSetToSync = updatedSet
            SparkCore.LOGGER.info("已在服务器端 OAnimationSet.ORIGINS 中添加/更新 '$rootLocation'。")
        }

        if (initialScanComplete) { // 关键检查点: 只有在初始扫描完成后才进行网络同步
            if (finalAnimationSetToSync != null && syncToPlayers) { // 对于删除，animationSet 是空的但非 null
                val syncPayload = OAnimationSetSyncPayload(rootLocation, finalAnimationSetToSync)
                PacketDistributor.sendToAllPlayers(syncPayload)
                SparkCore.LOGGER.info("已将 '$rootLocation' 的 OAnimationSet ($logOperation) 同步到所有客户端。")
            } else { // 理论上 compute 对于非删除操作不应返回 null
                SparkCore.LOGGER.warn("'$rootLocation' 在 OAnimationSet.ORIGINS 中更新后为 null (非删除操作)，未同步到客户端。文件: $filePath")
            }
        } else {
            // 初始扫描期间，只记录服务器端的操作，不发送网络包
            SparkCore.LOGGER.info("初始扫描阶段：服务器端已 $logOperation OAnimationSet '$rootLocation'，暂不网络同步。")
        }

        val previouslyRegisteredKeys = fileToRegisteredAnimationKeys.getOrDefault(filePath, mutableListOf()).toList()
        for (oldKey in previouslyRegisteredKeys) {
            if (typedAnimationRegistry.unregisterDynamic(oldKey)) {
                SparkCore.LOGGER.debug("注销了旧的 TypedAnimation: {} (文件: {})", oldKey.location(), filePath)
            } else {
                SparkCore.LOGGER.warn("尝试注销旧的 TypedAnimation {} 失败 (文件: {})", oldKey.location(), filePath)
            }
        }
        val currentFileAnimationKeys = mutableListOf<ResourceKey<TypedAnimation>>()
        fileToRegisteredAnimationKeys[filePath] = currentFileAnimationKeys

        for ((animNameInFile, _) in animationSet.animations.entries) {
            val specificAnimPath = "${rootLocation.path}/${animNameInFile.lowercase().replace(regex = Regex("[^a-z0-9/._-]"), "_")}"
            val animationSpecificLocation = ResourceLocation.fromNamespaceAndPath(rootLocation.namespace, specificAnimPath)
            SparkCore.LOGGER.debug(
                "动画文件 {} 的动画 '{}' 的 ResourceLocation: {}",
                filePath,
                animNameInFile,
                animationSpecificLocation
            )
            val animationKey = ResourceKey.create(typedAnimationRegistryKey, animationSpecificLocation)

            val animIndexForTypedAnimation = AnimIndex(rootLocation, animNameInFile)
            val typedAnimation = TypedAnimation(animIndexForTypedAnimation) {}

            try {
                typedAnimationRegistry.register(animationKey, typedAnimation, RegistrationInfo.BUILT_IN)
                currentFileAnimationKeys.add(animationKey)
                SparkCore.LOGGER.info("动态注册/更新了 TypedAnimation: {} (文件: {})", animationKey.location(), filePath)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("注册 TypedAnimation {} 时出错: {}", animationKey.location(), e.message, e)
            }
        }
    }
}