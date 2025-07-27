package cn.solarmoon.spark_core.resource.conflict

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * 资源冲突管理器
 * 
 * 负责检测资源文件的不一致性，管理Legacy文件夹，
 * 并提供冲突解决机制。
 */
object ResourceConflictManager {

    private val legacyDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("sparkcore_legacy")
    
    // 跟踪有冲突的资源
    private val conflictedResources = ConcurrentHashMap<String, ConflictInfo>()
    
    // 文件哈希缓存
    private val fileHashCache = ConcurrentHashMap<Path, String>()

    /**
     * 初始化冲突管理器
     */
    fun initialize() {
        // 确保Legacy目录存在
        if (!Files.exists(legacyDir)) {
            Files.createDirectories(legacyDir)
            SparkCore.LOGGER.info("创建Legacy目录: $legacyDir")
        }
        
        SparkCore.LOGGER.info("资源冲突管理器已初始化")
    }

    /**
     * 检测资源是否有冲突
     * @param resourceNode 要检查的资源节点
     * @param newContent 新的文件内容（可选）
     * @return 冲突检测结果
     */
    fun detectConflict(resourceNode: ResourceNode, newContent: ByteArray? = null): ConflictDetectionResult {
        val currentFile = resourceNode.basePath.resolve(resourceNode.relativePath)
        
        if (!currentFile.exists() || !currentFile.isRegularFile()) {
            return ConflictDetectionResult.NoConflict
        }
        
        val currentHash = getFileHash(currentFile)
        val legacyFile = getLegacyFilePath(resourceNode)
        
        // 检查是否已有Legacy版本
        if (legacyFile.exists()) {
            val legacyHash = getFileHash(legacyFile)
            
            // 如果当前文件与Legacy文件不同，说明有冲突
            if (currentHash != legacyHash) {
                val conflictInfo = ConflictInfo(
                    resourceNode = resourceNode,
                    currentFile = currentFile,
                    legacyFile = legacyFile,
                    currentHash = currentHash,
                    legacyHash = legacyHash,
                    detectedTime = System.currentTimeMillis()
                )
                
                conflictedResources[resourceNode.id.toString()] = conflictInfo
                return ConflictDetectionResult.Conflict(conflictInfo)
            }
        }
        
        // 如果提供了新内容，检查与当前文件的差异
        if (newContent != null) {
            val newHash = calculateHash(newContent)
            if (currentHash != newHash) {
                // 创建Legacy备份
                createLegacyBackup(resourceNode, currentFile)
                return ConflictDetectionResult.PendingChange(currentFile, newContent)
            }
        }
        
        return ConflictDetectionResult.NoConflict
    }

    /**
     * 创建Legacy备份
     */
    private fun createLegacyBackup(resourceNode: ResourceNode, sourceFile: Path) {
        try {
            val legacyFile = getLegacyFilePath(resourceNode)
            Files.createDirectories(legacyFile.parent)
            Files.copy(sourceFile, legacyFile, StandardCopyOption.REPLACE_EXISTING)
            
            SparkCore.LOGGER.info("创建Legacy备份: ${resourceNode.id} -> $legacyFile")
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("创建Legacy备份失败: ${resourceNode.id}", e)
        }
    }

    /**
     * 获取Legacy文件路径
     */
    private fun getLegacyFilePath(resourceNode: ResourceNode): Path {
        return legacyDir
            .resolve(resourceNode.modId)
            .resolve(resourceNode.moduleName)
            .resolve(resourceNode.relativePath)
    }

    /**
     * 获取文件哈希值
     */
    private fun getFileHash(filePath: Path): String {
        return fileHashCache.computeIfAbsent(filePath) {
            try {
                calculateHash(filePath.readBytes())
            } catch (e: Exception) {
                SparkCore.LOGGER.error("计算文件哈希失败: $filePath", e)
                ""
            }
        }
    }

    /**
     * 计算字节数组的哈希值
     */
    private fun calculateHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 解决冲突 - 使用当前版本
     */
    fun resolveConflictUseCurrent(resourceId: String) {
        val conflictInfo = conflictedResources[resourceId] ?: return
        
        try {
            // 删除Legacy文件
            Files.deleteIfExists(conflictInfo.legacyFile)
            conflictedResources.remove(resourceId)
            
            SparkCore.LOGGER.info("冲突已解决（使用当前版本）: $resourceId")
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("解决冲突失败: $resourceId", e)
        }
    }

    /**
     * 解决冲突 - 恢复Legacy版本
     */
    fun resolveConflictUseLegacy(resourceId: String) {
        val conflictInfo = conflictedResources[resourceId] ?: return
        
        try {
            // 用Legacy文件覆盖当前文件
            Files.copy(conflictInfo.legacyFile, conflictInfo.currentFile, StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(conflictInfo.legacyFile)
            conflictedResources.remove(resourceId)
            
            // 清除哈希缓存
            fileHashCache.remove(conflictInfo.currentFile)
            
            SparkCore.LOGGER.info("冲突已解决（恢复Legacy版本）: $resourceId")
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("解决冲突失败: $resourceId", e)
        }
    }

    /**
     * 获取所有冲突的资源
     */
    fun getAllConflicts(): Map<String, ConflictInfo> {
        return conflictedResources.toMap()
    }

    /**
     * 获取文件差异信息
     */
    fun getFileDiff(resourceId: String): FileDiffInfo? {
        val conflictInfo = conflictedResources[resourceId] ?: return null
        
        return try {
            val currentContent = Files.readString(conflictInfo.currentFile)
            val legacyContent = Files.readString(conflictInfo.legacyFile)
            
            FileDiffInfo(
                resourceId = resourceId,
                currentContent = currentContent,
                legacyContent = legacyContent,
                currentFile = conflictInfo.currentFile,
                legacyFile = conflictInfo.legacyFile
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("读取文件差异失败: $resourceId", e)
            null
        }
    }

    /**
     * 检查模块是否有待打包内容
     */
    fun hasModulePendingPackaging(moduleId: String): Boolean {
        return conflictedResources.values.any { 
            "${it.resourceNode.modId}:${it.resourceNode.moduleName}" == moduleId 
        }
    }

    /**
     * 获取模块的冲突资源列表
     */
    fun getModuleConflicts(moduleId: String): List<ConflictInfo> {
        return conflictedResources.values.filter { 
            "${it.resourceNode.modId}:${it.resourceNode.moduleName}" == moduleId 
        }
    }

    /**
     * 清理Legacy文件夹
     */
    fun cleanupLegacyFolder() {
        try {
            if (Files.exists(legacyDir)) {
                Files.walk(legacyDir).use { paths ->
                    paths.sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
                SparkCore.LOGGER.info("Legacy文件夹已清理")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("清理Legacy文件夹失败", e)
        }
    }

    // ==================== 数据类 ====================

    /**
     * 冲突检测结果
     */
    sealed class ConflictDetectionResult {
        object NoConflict : ConflictDetectionResult()
        data class Conflict(val conflictInfo: ConflictInfo) : ConflictDetectionResult()
        data class PendingChange(val targetFile: Path, val newContent: ByteArray) : ConflictDetectionResult()
    }

    /**
     * 冲突信息
     */
    data class ConflictInfo(
        val resourceNode: ResourceNode,
        val currentFile: Path,
        val legacyFile: Path,
        val currentHash: String,
        val legacyHash: String,
        val detectedTime: Long
    )

    /**
     * 文件差异信息
     */
    data class FileDiffInfo(
        val resourceId: String,
        val currentContent: String,
        val legacyContent: String,
        val currentFile: Path,
        val legacyFile: Path
    )
}
