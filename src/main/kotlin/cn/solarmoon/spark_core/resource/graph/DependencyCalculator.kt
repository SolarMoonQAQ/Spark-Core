package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import net.minecraft.resources.ResourceLocation
import org.jgrapht.graph.SimpleDirectedGraph

/**
 * 依赖计算器
 * 
 * 专门负责依赖关系的计算和规则应用，遵循单一职责原则。
 * 提供依赖规则的管理、依赖关系的计算和图边的管理功能。
 */
object DependencyCalculator {
    
    /**
     * 依赖规则定义
     */
    private data class DependencyRule(
        val name: String,
        val description: String,
        val predicate: (ResourceLocation, String) -> Boolean,
        val builder: (ResourceLocation, String) -> List<OResourceDependency>
    )
    
    /**
     * 默认依赖规则集合
     */
    private val defaultRules = listOf(
        
        // 规则1：动画对同名且满足骨骼要求的模型的硬依赖
        DependencyRule(
            name = "animation_to_model_hard",
            description = "动画对同名且满足骨骼名和数量的模型的硬依赖",
            predicate = { resourceLocation, resourceType ->
                resourceType == "animation" && resourceLocation.path.contains("animations/")
            },
            builder = { resourceLocation, _ ->
                val dependencies = mutableListOf<OResourceDependency>()
                val animationPath = resourceLocation.path

                // 提取模型名称 (如 sparkcore/animations/player/base_state.json -> player)
                val modelName = extractModelNameFromAnimationPath(animationPath)
                val namespace = resourceLocation.namespace

                // 提取模块名 (如 sparkcore/animations/player/base_state.json -> sparkcore)
                val moduleName = extractModuleNameFromPath(animationPath)

                // 对应的模型文件位置 - 四层格式（不包含扩展名）
                val modelLocation = ResourceLocation.fromNamespaceAndPath(
                    namespace,
                    "$moduleName/models/$modelName"
                )
                
                // 验证骨骼兼容性
                val isCompatible = validateBoneCompatibility(resourceLocation, modelLocation)
                
                dependencies.add(
                    OResourceDependency(
                        id = modelLocation,
                        type = if (isCompatible) ODependencyType.HARD else ODependencyType.SOFT,
                        path = modelLocation.path,
                        extraProps = mapOf(
                            "auto_generated" to true,
                            "rule" to "animation_to_model_hard",
                            "bone_validation" to isCompatible,
                            "required_bones" to getRequiredBonesFromAnimation(resourceLocation)
                        )
                    )
                )
                dependencies
            }
        ),
        
        // 规则2：模型对同名动画文件夹的可选依赖
        DependencyRule(
            name = "model_to_animations_optional",
            description = "模型对同名动画文件夹的可选依赖",
            predicate = { resourceLocation, resourceType ->
                resourceType == "model" && resourceLocation.path.contains("models/")
            },
            builder = { resourceLocation, _ ->
                val dependencies = mutableListOf<OResourceDependency>()
                val modelName = extractModelNameFromPath(resourceLocation.path)
                val namespace = resourceLocation.namespace

                // 提取模块名
                val moduleName = extractModuleNameFromPath(resourceLocation.path)

                // 查找对应的动画文件夹中的所有动画 - 四层格式
                val animationFolderPath = "$moduleName/animations/$modelName"
                val animationDependencies = findAnimationsInFolder(namespace, animationFolderPath)
                
                for (animationLocation in animationDependencies) {
                    dependencies.add(
                        OResourceDependency(
                            id = animationLocation,
                            type = ODependencyType.SOFT,
                            path = animationLocation.path,
                            extraProps = mapOf(
                                "auto_generated" to true,
                                "rule" to "model_to_animations_optional",
                                "animation_folder" to animationFolderPath
                            )
                        )
                    )
                }
                
                dependencies
            }
        ),
        
        // 规则3：模型对同名贴图的可选依赖
        DependencyRule(
            name = "model_to_texture_optional",
            description = "模型对同名贴图的可选依赖",
            predicate = { resourceLocation, resourceType ->
                resourceType == "model" && resourceLocation.path.contains("models/")
            },
            builder = { resourceLocation, _ ->
                val dependencies = mutableListOf<OResourceDependency>()
                val modelName = extractModelNameFromPath(resourceLocation.path)
                val namespace = resourceLocation.namespace

                // 提取模块名
                val moduleName = extractModuleNameFromPath(resourceLocation.path)

                // 对同名贴图文件的可选依赖 - 四层格式（不包含扩展名）
                val textureLocation = ResourceLocation.fromNamespaceAndPath(
                    namespace,
                    "$moduleName/textures/$modelName"
                )
                
                dependencies.add(
                    OResourceDependency(
                        id = textureLocation,
                        type = ODependencyType.SOFT,
                        path = textureLocation.path,
                        extraProps = mapOf(
                            "auto_generated" to true,
                            "rule" to "model_to_texture_optional"
                        )
                    )
                )
                
                dependencies
            }
        ),
        
        // 规则4：JS脚本对动画路径的依赖
        DependencyRule(
            name = "js_to_animation_dependencies",
            description = "JS脚本中提取动画路径依赖",
            predicate = { resourceLocation, resourceType ->
                resourceType == "script" && resourceLocation.path.endsWith(".js")
            },
            builder = { resourceLocation, _ ->
                val dependencies = mutableListOf<OResourceDependency>()
                
                // 提取JS文件中的动画路径引用
                val animationReferences = extractAnimationReferencesFromJS(resourceLocation)
                
                for (animationRef in animationReferences) {
                    val animationLocation = ResourceLocation.parse(animationRef)
                    val dependencyType = if (resourceExists(animationLocation)) {
                        ODependencyType.HARD
                    } else {
                        ODependencyType.SOFT
                    }
                    
                    dependencies.add(
                        OResourceDependency(
                            id = animationLocation,
                            type = dependencyType,
                            path = animationLocation.path,
                            extraProps = mapOf(
                                "auto_generated" to true,
                                "rule" to "js_to_animation_dependencies",
                                "source" to "js_reference"
                            )
                        )
                    )
                }
                
                dependencies
            }
        )
    )
    
    /**
     * 为给定的资源节点计算依赖关系
     * 
     * @param node 资源节点
     * @return 计算出的依赖关系列表
     */
    fun calculateDependencies(node: ResourceNode): List<OResourceDependency> {
        val resourceType = inferResourceType(node.id)
        return applyRules(node.id, resourceType)
    }
    
    /**
     * 应用依赖规则生成依赖关系
     * 
     * @param resourceLocation 资源位置
     * @param resourceType 资源类型
     * @return 生成的依赖关系列表
     */
    fun applyRules(resourceLocation: ResourceLocation, resourceType: String): List<OResourceDependency> {
        val allDependencies = mutableListOf<OResourceDependency>()
        
        for (rule in defaultRules) {
            try {
                if (rule.predicate(resourceLocation, resourceType)) {
                    val dependencies = rule.builder(resourceLocation, resourceType)
                    allDependencies.addAll(dependencies)
                    
                    SparkCore.LOGGER.debug("为资源 $resourceLocation 应用规则 '${rule.name}'，生成 ${dependencies.size} 个依赖")
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("应用依赖规则 '${rule.name}' 时出错: ${e.message}", e)
            }
        }
        
        return allDependencies.distinctBy { it.id } // 去除重复依赖
    }
    
    /**
     * 在图中链接依赖关系
     * 
     * @param graph 依赖图
     * @param sourceNode 源节点
     * @param dependencies 依赖关系列表
     */
    fun linkDependencies(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>, 
        sourceNode: ResourceNode, 
        dependencies: List<OResourceDependency>
    ) {
        // 移除旧的依赖边
        val edgesToRemove = graph.outgoingEdgesOf(sourceNode).toSet()
        graph.removeAllEdges(edgesToRemove)
        
        // 创建新的依赖边
        for (dep in dependencies) {
            val targetNode = ResourceNodeManager.getNode(dep.id)
            if (targetNode != null) {
                val edgeType = when (dep.type) {
                    ODependencyType.HARD -> EdgeType.HARD_DEPENDENCY
                    ODependencyType.SOFT, ODependencyType.OPTIONAL -> EdgeType.SOFT_DEPENDENCY
                }
                graph.addEdge(sourceNode, targetNode, edgeType)
            } else {
                SparkCore.LOGGER.debug("依赖目标节点不存在，跳过链接: ${sourceNode.id} -> ${dep.id}")
            }
        }
    }
    
    /**
     * 推断资源类型
     */
    private fun inferResourceType(resourceKey: ResourceLocation): String {
        return when {
            resourceKey.path.contains("animations/") -> "animation"
            resourceKey.path.contains("models/") -> "model"
            resourceKey.path.contains("textures/") -> "texture"
            resourceKey.path.contains("script/") -> "script"
            resourceKey.path.contains("ik_constraints/") -> "ik_constraint"
            resourceKey.path.endsWith("module_info.json") -> "module_descriptor"
            else -> "unknown"
        }
    }

    // =================================================================
    //  辅助方法
    // =================================================================

    /**
     * 从动画路径提取模型名称
     * 如 sparkcore/animations/player/base_state.json -> player
     */
    private fun extractModelNameFromAnimationPath(animationPath: String): String {
        return when {
            animationPath.contains("/animations/") -> {
                val pathAfterAnimations = animationPath.substringAfter("/animations/")
                pathAfterAnimations.substringBefore("/")
            }
            else -> {
                // 兜底逻辑
                animationPath.substringAfterLast("/").substringBefore(".")
            }
        }
    }

    /**
     * 从路径中提取模块名
     * 如 sparkcore/animations/player/base_state.json -> sparkcore
     * 如 sparkcore/models/player.json -> sparkcore
     */
    private fun extractModuleNameFromPath(resourcePath: String): String {
        return resourcePath.substringBefore("/")
    }

    /**
     * 从模型路径提取模型名称
     * 如 sparkcore/models/player.json -> player
     */
    private fun extractModelNameFromPath(modelPath: String): String {
        return when {
            modelPath.contains("/models/") -> {
                val pathAfterModels = modelPath.substringAfter("/models/")
                pathAfterModels.substringBefore(".").substringBefore("/")
            }
            else -> {
                // 兜底逻辑
                modelPath.substringAfterLast("/").substringBefore(".")
            }
        }
    }

    /**
     * 验证动画与模型的骨骼兼容性
     * 使用现有的OModel和OAnimationSet类来检查骨骼匹配
     */
    private fun validateBoneCompatibility(animationLocation: ResourceLocation, modelLocation: ResourceLocation): Boolean {
        try {
            // 获取模型的骨骼信息
            val model = cn.solarmoon.spark_core.animation.model.origin.OModel.get(modelLocation)
            if (model == cn.solarmoon.spark_core.animation.model.origin.OModel.EMPTY) {
                SparkCore.LOGGER.warn("模型不存在或为空: $modelLocation")
                return false
            }

            // 获取动画的骨骼信息
            val animationSet = cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet.get(animationLocation)
            if (animationSet == cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet.EMPTY) {
                SparkCore.LOGGER.warn("动画不存在或为空: $animationLocation")
                return false
            }

            // 检查骨骼兼容性
            val modelBones = model.bones.keys.toSet()
            val animationBones = animationSet.animations.values.flatMap { it.bones.keys }.toSet()

            // 计算匹配度
            val matchingBones = modelBones.intersect(animationBones)
            val matchRatio = if (animationBones.isNotEmpty()) {
                matchingBones.size.toDouble() / animationBones.size
            } else {
                0.0
            }

            // 如果匹配度超过80%，认为兼容
            val isCompatible = matchRatio >= 0.8

            SparkCore.LOGGER.debug("骨骼兼容性检查: $animationLocation -> $modelLocation, 匹配度: ${String.format("%.2f", matchRatio * 100)}%, 兼容: $isCompatible")

            return isCompatible
        } catch (e: Exception) {
            SparkCore.LOGGER.error("验证骨骼兼容性时出错: $animationLocation -> $modelLocation", e)
            return false
        }
    }

    /**
     * 从动画中获取所需的骨骼列表
     */
    private fun getRequiredBonesFromAnimation(animationLocation: ResourceLocation): List<String> {
        return try {
            val animationSet = cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet.get(animationLocation)
            if (animationSet != cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet.EMPTY) {
                animationSet.animations.values.flatMap { it.bones.keys }.distinct()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("获取动画所需骨骼时出错: $animationLocation", e)
            emptyList()
        }
    }

    /**
     * 在指定文件夹中查找动画文件
     */
    private fun findAnimationsInFolder(namespace: String, folderPath: String): List<ResourceLocation> {
        // 这里应该实现实际的文件系统搜索逻辑
        // 暂时返回空列表，具体实现需要根据项目的资源发现机制
        return emptyList()
    }

    /**
     * 从JS文件中提取动画引用
     */
    private fun extractAnimationReferencesFromJS(resourceLocation: ResourceLocation): List<String> {
        // 这里应该实现JS文件解析逻辑
        // 暂时返回空列表，具体实现需要根据JS文件的格式
        return emptyList()
    }

    /**
     * 检查资源是否存在
     */
    private fun resourceExists(resourceLocation: ResourceLocation): Boolean {
        return ResourceNodeManager.containsNode(resourceLocation)
    }
}
