package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.common.ModuleIdUtils
import cn.solarmoon.spark_core.resource.origin.OAssetMetadata
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import net.minecraft.resources.ResourceLocation
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.traverse.DepthFirstIterator
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import java.io.File
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

/**
 * 资源图管理器 (重构后)
 *
 * 负责管理所有具体资源（模型、动画等）的依赖和覆盖关系。
 * 这是一个被动的数据管理器，由外部的资源处理器（ResourceHandler）填充数据。
 */
object ResourceGraphManager {

    private val graph = SimpleDirectedGraph<ResourceNode, EdgeType>(EdgeType::class.java)
    private val gson = Gson()

    // 延迟依赖计算机制
    private val pendingDependencyNodes = mutableListOf<ResourceNode>()
    private var isDependencySystemInitialized = false

    /**
     * 注册或更新一个资源。
     * 这是外部处理器与图交互的主要入口点。
     *
     * @param filePath 资源文件的物理路径。
     * @param resourceType 资源的类型 (e.g., "animations", "models")。
     * @return 创建或更新后的ResourceNode，如果失败则返回null。
     */
    fun addOrUpdateResource(filePath: Path, resourceType: String): ResourceNode? {
        val pathInfo = ResourcePathResolver.resolveResourcePath(filePath, resourceType)
        if (pathInfo == null) {
            SparkCore.LOGGER.error("无法为文件解析资源路径: $filePath")
            return null
        }

        // 2. 加载元数据 (委托给 ResourceMetadataLoader)
        val metadata = ResourceMetadataLoader.loadMetadataFor(filePath)

        // 3. 检查节点是否已存在
        val existingNode = ResourceNodeManager.getNode(pathInfo.resourceLocation)
        if (existingNode != null) {
            // 更新现有节点
            existingNode.provides = metadata.provides
            existingNode.tags = metadata.tags
            existingNode.properties = metadata.properties
            ResourceNodeManager.updateNode(existingNode)
            // 触发依赖重新计算
            generateAndLinkDependencies(existingNode)
            return existingNode
        } else {
            // 4. 创建新节点
            val newNode = ResourceNode(
                id = pathInfo.resourceLocation,
                provides = metadata.provides,
                tags = metadata.tags,
                properties = metadata.properties,
                namespace = pathInfo.namespace,
                resourcePath = pathInfo.resourcePath,
                sourceType = pathInfo.sourceType,
                basePath = pathInfo.basePath,
                relativePath = pathInfo.relativePath,
                modId = pathInfo.modId,
                moduleName = pathInfo.moduleName
            )
            ResourceNodeManager.addNode(newNode)
            graph.addVertex(newNode)

            // 延迟依赖计算：只有在依赖系统初始化后才计算依赖
            if (isDependencySystemInitialized) {
                generateAndLinkDependencies(newNode)
            } else {
                // 将节点加入待处理队列
                pendingDependencyNodes.add(newNode)
                SparkCore.LOGGER.debug("资源节点已添加到待处理队列: ${newNode.id}")
            }

            // 通知覆盖系统节点变动（如果已初始化）
            if (OverrideManager.isOverrideSystemInitialized()) {
                // 检查新节点是否影响现有覆盖关系
                checkAndNotifyOverrideChanges(newNode)
            }

            return newNode
        }
    }

    /**
     * 移除一个资源及其所有相关边。
     * @param resourceId 要移除的资源的ID。
     */
    fun removeResource(resourceId: ResourceLocation): Boolean {
        val nodeToRemove = ResourceNodeManager.removeNode(resourceId)
        if (nodeToRemove != null) {
            // 在移除资源前，清理相关的模块依赖 (委托给 ModuleDependencySync)
            ModuleDependencySync.cleanupModuleDependenciesForResource(graph, nodeToRemove)

            // 通知覆盖系统节点移除（如果已初始化）
            if (OverrideManager.isOverrideSystemInitialized()) {
                checkAndNotifyOverrideChanges(nodeToRemove)
            }

            graph.removeVertex(nodeToRemove)
            return true
        }
        return false
    }

    /**
     * 获取一个节点的直接依赖。
     */
    fun getDirectDependencies(resourceId: ResourceLocation): List<OResourceDependency> {
        val node = ResourceNodeManager.getNode(resourceId) ?: return emptyList()
        return graph.outgoingEdgesOf(node).mapNotNull { edge ->
            val edgeType = graph.getEdge(node, graph.getEdgeTarget(edge))
            val dependencyNode = graph.getEdgeTarget(edge)
            
            if (dependencyNode != null && (edgeType == EdgeType.HARD_DEPENDENCY || edgeType == EdgeType.SOFT_DEPENDENCY)) {
                OResourceDependency(
                    id = dependencyNode.id,
                    type = if (edgeType == EdgeType.HARD_DEPENDENCY) ODependencyType.HARD else ODependencyType.SOFT,
                    path = dependencyNode.id.path
                )
            } else {
                null
            }
        }
    }

    /**
     * 添加一个覆盖规则到图中。
     */
    fun addOverride(source: ResourceLocation, target: ResourceLocation): Boolean {
        return OverrideManager.addOverrideToGraph(graph, source, target)
    }

    /**
     * 获取一个资源的所有覆盖规则 (即，哪些资源覆盖了它)。
     */
    fun getOverridesFor(resourceId: ResourceLocation): List<ResourceNode> {
        return OverrideManager.getOverridesFor(graph, resourceId)
    }

    /**
     * 获取所有覆盖规则。
     */
    fun getAllOverrides(): Map<ResourceNode, List<ResourceNode>> {
        return OverrideManager.getAllOverrides(graph)
    }

    /**
     * 获取依赖图实例
     * 用于其他组件访问图结构
     */
    fun getGraph(): SimpleDirectedGraph<ResourceNode, EdgeType> {
        return graph
    }


    /**
     * 根据物理文件路径查找资源节点。
     * 注意：这个操作可能较慢，因为它需要遍历所有已知资源。
     */
    fun findNodeByPath(filePath: Path): ResourceNode? {
        val normalizedPathStr = filePath.normalize().pathString
        return ResourceNodeManager.getAllNodeValues().find {
            val nodePath = it.basePath.resolve(it.relativePath).normalize().pathString
            nodePath == normalizedPathStr
        }
    }


    /**
     * 为给定的资源节点生成依赖关系并更新图。
     * (委托给 DependencyCalculator 处理)
     */
    private fun generateAndLinkDependencies(node: ResourceNode) {
        // 1. 计算依赖关系
        val dependencies = DependencyCalculator.calculateDependencies(node)

        // 2. 在图中链接依赖关系
        DependencyCalculator.linkDependencies(graph, node, dependencies)

        // 3. 同步跨模块依赖到ModuleGraphManager (委托给 ModuleDependencySync)
        ModuleDependencySync.syncModuleDependencies(node, dependencies)
    }

    /**
     * 注册一个模块的所有资源及其依赖关系。
     * 这是向资源图填充数据的核心入口。
     *
     * @param moduleInfo 包含模块所有资源元数据和依赖信息的对象。
     */
    fun registerModuleResources(moduleInfo: OModuleInfo) {
        // 第一步：注册所有资源节点
        moduleInfo.resourceDependencies.keys.forEach { resourceId ->
            val metadata = OAssetMetadata(id = resourceId.toString()) // 创建一个基础元数据
            registerResource(metadata)
        }
        
        // 第二步：根据依赖信息构建边
        moduleInfo.resourceDependencies.forEach { (sourceId, dependencies) ->
            val sourceNode = getResourceNode(sourceId) ?: return@forEach

            dependencies.forEach { dependency ->
                val depNode = getResourceNode(dependency.id) ?: return@forEach
                
                val edgeType = when(dependency.type) {
                    ODependencyType.HARD -> EdgeType.HARD_DEPENDENCY
                    ODependencyType.SOFT, ODependencyType.OPTIONAL -> EdgeType.SOFT_DEPENDENCY
                }
                graph.addEdge(sourceNode, depNode, edgeType)
            }
        }
    }
    
    /**
     * 注册一个新资源节点。如果已存在，则返回现有节点。
     * 这是一个简化的注册方法，用于registerModuleResources
     */
    private fun registerResource(metadata: OAssetMetadata): ResourceNode {
        val resourceId = ResourceLocation.parse(metadata.id)

        // 检查节点是否已存在
        val existingNode = ResourceNodeManager.getNode(resourceId)
        if (existingNode != null) {
            return existingNode
        }

        // 创建新节点（使用简化的信息）
        val newNode = ResourceNode(
            id = resourceId,
            provides = metadata.provides,
            tags = metadata.tags,
            properties = metadata.properties,
            namespace = resourceId.namespace,
            resourcePath = resourceId.path,
            sourceType = ResourceSourceType.LOOSE_FILES, // 默认类型
            basePath = java.nio.file.Paths.get(""), // 空路径
            relativePath = java.nio.file.Paths.get(resourceId.path),
            modId = resourceId.namespace, // modId等于namespace
            moduleName = resourceId.namespace // 简化情况下使用namespace作为moduleName
        )

        ResourceNodeManager.addNode(newNode)
        graph.addVertex(newNode)

        return newNode
    }

    /**
     * 获取指定资源的节点。
     */
    fun getResourceNode(resourceId: ResourceLocation): ResourceNode? {
        return ResourceNodeManager.getNode(resourceId)
    }
    
    /**
     * 获取一个资源的所有依赖（直接和间接）。
     *
     * @param startResourceId 起始资源的ID。
     * @param hardOnly 是否只追踪硬依赖。
     * @return 所有依赖节点的集合。
     */
    fun getAllDependencies(startResourceId: ResourceLocation, hardOnly: Boolean = true): Set<ResourceNode> {
        val startNode = getResourceNode(startResourceId) ?: return emptySet()
        
        val graphView = if (hardOnly) {
            AsSubgraph<ResourceNode, EdgeType>(graph, graph.vertexSet(), graph.edgeSet().filter { it == EdgeType.HARD_DEPENDENCY }.toSet())
        } else {
            graph
        }
        
        val traversal = DepthFirstIterator(graphView, startNode)
        val allDeps = mutableSetOf<ResourceNode>()

        while (traversal.hasNext()) {
            val currentNode = traversal.next()
            if (currentNode != startNode) {
                allDeps.add(currentNode)
            }
        }
        return allDeps
    }


    /**
     * 获取直接依赖于指定资源的节点（反向依赖）。
     */
    fun getDirectDependents(resourceId: ResourceLocation): Map<ResourceNode, EdgeType> {
        val node = ResourceNodeManager.getNode(resourceId) ?: return emptyMap()
        return graph.incomingEdgesOf(node).associate { Pair(graph.getEdgeSource(it), it) }
    }
    
    /**
     * 清理所有数据。
     */
    fun clear() {
        ResourceNodeManager.clear()
        val verticesToRemove = graph.vertexSet().toSet()
        verticesToRemove.forEach { graph.removeVertex(it) }
        SparkCore.LOGGER.info("ResourceGraphManager 已被清理。")
    }


    /**
     * 检查硬依赖是否满足
     */
    fun hasHardDependencyFailures(resourceLocation: ResourceLocation): Boolean {
        return ResourceValidator.hasHardDependencyFailures(resourceLocation)
    }


    /**
     * 验证严重性级别
     */
    enum class ValidationSeverity {
        IGNORE,     // 忽略
        LOG,        // 仅记录日志
        WARN,       // 警告
        ERROR       // 错误
    }

    /**
     * 验证配置
     */
    data class ValidationConfig(
        val checkHardDependencies: Boolean = true,
        val checkSoftDependencies: Boolean = true,
        val checkOptionalDependencies: Boolean = false,
        val logLevel: ValidationSeverity = ValidationSeverity.LOG,
        val failOnHardDependencyMissing: Boolean = true,
        val failOnVersionMismatch: Boolean = false,
        val enableDetailedLogging: Boolean = false
    )

    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val resource: ResourceLocation,
        val results: List<DependencyValidationResult>,
        val errors: List<String>,
        val warnings: List<String>,
        val summary: ValidationSummary
    )

    /**
     * 验证摘要
     */
    data class ValidationSummary(
        val totalDependencies: Int,
        val successCount: Int,
        val missingCount: Int,
        val versionMismatchCount: Int,
        val hardDependencyFailures: Int,
        val softDependencyFailures: Int,
        val optionalDependencyFailures: Int
    )

    /**
     * 验证资源的所有依赖
     * 委托给ResourceValidator
     */
    fun validateResource(
        resourceLocation: ResourceLocation,
        config: ValidationConfig = ValidationConfig()
    ): ValidationResult {
        // 转换配置
        val validatorConfig = ResourceValidator.ValidationConfig(
            checkHardDependencies = config.checkHardDependencies,
            checkSoftDependencies = config.checkSoftDependencies,
            checkOptionalDependencies = config.checkOptionalDependencies,
            logLevel = when (config.logLevel) {
                ValidationSeverity.IGNORE -> ResourceValidator.ValidationSeverity.IGNORE
                ValidationSeverity.LOG -> ResourceValidator.ValidationSeverity.LOG
                ValidationSeverity.WARN -> ResourceValidator.ValidationSeverity.WARN
                ValidationSeverity.ERROR -> ResourceValidator.ValidationSeverity.ERROR
            },
            failOnHardDependencyMissing = config.failOnHardDependencyMissing,
            failOnVersionMismatch = config.failOnVersionMismatch,
            enableDetailedLogging = config.enableDetailedLogging
        )

        // 调用ResourceValidator
        val validatorResult = ResourceValidator.validateResource(resourceLocation, validatorConfig)

        // 转换结果
        return ValidationResult(
            isValid = validatorResult.isValid,
            resource = validatorResult.resource,
            results = validatorResult.results.map { validatorResult ->
                DependencyValidationResult(
                    isValid = validatorResult.isValid,
                    errorMessage = validatorResult.errorMessage
                )
            },
            errors = validatorResult.errors,
            warnings = validatorResult.warnings,
            summary = ValidationSummary(
                totalDependencies = validatorResult.summary.totalDependencies,
                successCount = validatorResult.summary.successCount,
                missingCount = validatorResult.summary.missingCount,
                versionMismatchCount = validatorResult.summary.versionMismatchCount,
                hardDependencyFailures = validatorResult.summary.hardDependencyFailures,
                softDependencyFailures = validatorResult.summary.softDependencyFailures,
                optionalDependencyFailures = validatorResult.summary.optionalDependencyFailures
            )
        )
    }

    /**
     * 清理验证缓存
     * 委托给ResourceValidator
     */
    fun clearValidationCache() {
        ResourceValidator.clearValidationCache()
    }


    /**
     * 检查节点变动是否影响覆盖关系并通知覆盖系统
     */
    private fun checkAndNotifyOverrideChanges(node: ResourceNode) {
        // 检查是否需要建立新的覆盖关系
        detectAndAddOverrideRelations(node)
    }

    /**
     * 检测并添加覆盖关系
     * 根据资源路径和模块信息自动检测覆盖关系
     */
    private fun detectAndAddOverrideRelations(node: ResourceNode) {
        try {
            // 查找同名但不同模块的资源，建立覆盖关系
            val sameNameNodes = ResourceNodeManager.getAllNodeValues().filter { otherNode ->
                otherNode != node &&
                isSameResourceType(node, otherNode) &&
                isSameResourceName(node, otherNode) &&
                otherNode.modId != node.modId
            }

            for (otherNode in sameNameNodes) {
                // 根据模块优先级决定覆盖方向
                val shouldOverride = shouldNodeOverride(node, otherNode)
                if (shouldOverride) {
                    // node覆盖otherNode
                    val success = OverrideManager.addOverrideToGraph(graph, node.id, otherNode.id)
                    if (success) {
                        SparkCore.LOGGER.debug("自动检测到覆盖关系: ${node.id} -> ${otherNode.id}")
                    }
                } else {
                    // otherNode覆盖node
                    val success = OverrideManager.addOverrideToGraph(graph, otherNode.id, node.id)
                    if (success) {
                        SparkCore.LOGGER.debug("自动检测到覆盖关系: ${otherNode.id} -> ${node.id}")
                    }
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("检测覆盖关系时出错: ${node.id}", e)
        }
    }

    /**
     * 判断两个节点是否是同一类型的资源
     */
    private fun isSameResourceType(node1: ResourceNode, node2: ResourceNode): Boolean {
        // 从路径中提取资源类型
        val type1 = extractResourceTypeFromPath(node1.resourcePath)
        val type2 = extractResourceTypeFromPath(node2.resourcePath)
        return type1 == type2
    }

    /**
     * 判断两个节点是否是同名资源
     */
    private fun isSameResourceName(node1: ResourceNode, node2: ResourceNode): Boolean {
        // 从路径中提取资源名称（去除模块名和资源类型）
        val name1 = extractResourceNameFromPath(node1.resourcePath)
        val name2 = extractResourceNameFromPath(node2.resourcePath)
        return name1 == name2
    }

    /**
     * 从资源路径中提取资源类型
     */
    private fun extractResourceTypeFromPath(resourcePath: String): String? {
        // 格式：moduleName/resourceType/path
        val parts = resourcePath.split("/")
        return if (parts.size >= 2) parts[1] else null
    }

    /**
     * 从资源路径中提取资源名称
     */
    private fun extractResourceNameFromPath(resourcePath: String): String? {
        // 格式：moduleName/resourceType/path
        val parts = resourcePath.split("/")
        return if (parts.size >= 3) parts.drop(2).joinToString("/") else null
    }

    /**
     * 判断node1是否应该覆盖node2
     * 基于模块优先级决定
     */
    private fun shouldNodeOverride(node1: ResourceNode, node2: ResourceNode): Boolean {
        // 获取模块的拓扑排序，优先级高的模块在后面
        val sortedModules = ModuleGraphManager.getModulesInTopologicalOrder().map { it.id }

        val index1 = sortedModules.indexOf(node1.modId)
        val index2 = sortedModules.indexOf(node2.modId)

        // 如果都在排序中，优先级高的（索引大的）覆盖优先级低的
        return when {
            index1 != -1 && index2 != -1 -> index1 > index2
            index1 != -1 && index2 == -1 -> true  // 已知模块覆盖未知模块
            index1 == -1 && index2 != -1 -> false // 未知模块不覆盖已知模块
            else -> node1.modId > node2.modId // 都是未知模块，按字母排序
        }
    }

    /**
     * 初始化依赖系统
     * 在所有资源节点加载完成后调用，开始计算依赖关系
     */
    fun initializeDependencySystem() {
        SparkCore.LOGGER.info("ResourceGraphManager: 初始化依赖系统...")
        isDependencySystemInitialized = true

        // 处理所有待计算依赖的节点
        val nodesToProcess = pendingDependencyNodes.toList()
        pendingDependencyNodes.clear()

        SparkCore.LOGGER.info("开始为 ${nodesToProcess.size} 个资源节点计算依赖关系...")

        for (node in nodesToProcess) {
            try {
                generateAndLinkDependencies(node)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("为节点 ${node.id} 计算依赖时出错", e)
            }
        }

        SparkCore.LOGGER.info("依赖系统初始化完成")
    }

    /**
     * 初始化覆盖系统
     * 在所有资源加载完成后调用
     */
    fun initializeOverrideSystem() {
        SparkCore.LOGGER.info("ResourceGraphManager: 初始化覆盖系统...")
        OverrideManager.initializeOverrideSystem()
    }

}

/**
 * 依赖验证结果
 * ResourceGraphManager的验证结果类型
 */
data class DependencyValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)