package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.resource.graph.ModuleNode
import net.neoforged.bus.api.Event

/**
 * 模块图变更事件
 * 当 ModuleGraphManager 中的模块节点发生变化时触发
 */
abstract class ModuleGraphEvent : Event() {

    /**
     * 模块节点变更事件
     * @param moduleNode 发生变更的模块节点
     * @param changeType 变更类型
     */
    class NodeChange(
        val moduleNode: ModuleNode,
        val changeType: ChangeType
    ) : ModuleGraphEvent()

    /**
     * 模块依赖关系变更事件
     * @param source 依赖源模块节点
     * @param target 依赖目标模块节点
     * @param dependencyType 依赖类型 (HARD, SOFT)
     * @param changeType 变更类型
     */
    class DependencyChange(
        val source: ModuleNode,
        val target: ModuleNode,
        val dependencyType: DependencyType,
        val changeType: ChangeType
    ) : ModuleGraphEvent()

    /**
     * 模块加载顺序变更事件
     * 当模块的加载顺序发生变化时触发
     */
    class LoadOrderChange(
        val affectedModules: List<ModuleNode>
    ) : ModuleGraphEvent()

    /**
     * 模块图重建事件
     * 当整个模块图被重新构建时触发
     */
    object GraphRebuilt : ModuleGraphEvent()

    /**
     * 变更类型枚举
     */
    enum class ChangeType {
        /** 添加 */
        ADDED,
        /** 更新 */
        UPDATED,
        /** 移除 */
        REMOVED
    }

    /**
     * 模块依赖类型枚举
     */
    enum class DependencyType {
        /** 硬依赖 */
        HARD,
        /** 软依赖 */
        SOFT
    }
}
