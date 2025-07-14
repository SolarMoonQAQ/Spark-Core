package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path

/**
 * 资源来源类型
 */
enum class ResourceSourceType {
    LOOSE_FILES,    // 松散文件 (spark_core/namespace/)
    MOD_ASSETS,     // MOD资源 (assets/namespace/)
    SPARK_PACKAGE,  // .spark包文件
}


/**
 * 代表一个模块的图节点（业务类）。
 *
 * 它持有模块的静态数据 `OModuleInfo`，并可以包含运行时的状态和逻辑。
 * 这个类取代了旧的 MetadataContainer。
 *
 * @param data 模块的静态元数据 (OModuleInfo)。
 */
class ModuleNode(val data: OModuleInfo) {
    val id: String
        get() = data.id

    // 未来可以添加模块的运行时状态，例如
    // var runtimeState: ModuleRuntimeState = ModuleRuntimeState.LOADED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ModuleNode
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "ModuleNode(id=$id)"
    }
}

/**
 * 代表一个资源的图节点（业务类）。
 *
 * 它持有资源的所有相关信息，包括逻辑身份、物理路径和元数据。
 * 这个类取代了 OAssetMetadata 和 ResourcePathInfo 的组合。
 * 支持四层目录结构的模块信息。
 *
 * @param id 资源的唯一ID (ResourceLocation)。
 * @param provides 提供的接口或特性。
 * @param tags 标签。
 * @param properties 额外属性。
 * @param namespace 资源所属的命名空间。
 * @param resourcePath 资源路径 (不含命名空间)。
 * @param sourceType 资源的来源类型。
 * @param basePath 资源的基准路径。
 * @param relativePath 资源相对于基准路径的相对路径。
 * @param modId 模组ID，用于四层目录结构。
 * @param moduleName 模块名称，用于四层目录结构。
 */
class ResourceNode(
    val id: ResourceLocation,
    // 元数据字段 (来自 OAssetMetadata)
    var provides: List<String> = emptyList(),
    var tags: List<String> = emptyList(),
    var properties: Map<String, Any> = emptyMap(),
    // 物理路径字段 (来自 ResourcePathInfo)
    val namespace: String,
    val resourcePath: String,
    val sourceType: ResourceSourceType,
    val basePath: Path,
    val relativePath: Path,
    // 新增字段：支持四层目录结构
    val modId: String,        // modId等于namespace
    val moduleName: String    // 从路径中提取的模块名
) {
    /**
     * 获取完整的模块标识
     * 格式：{modId}:{moduleName}
     */
    fun getFullModuleId(): String = "$modId:$moduleName"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ResourceNode
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "ResourceNode(id=$id, modId=$modId, moduleName=$moduleName, path=$basePath/$relativePath)"
    }
}