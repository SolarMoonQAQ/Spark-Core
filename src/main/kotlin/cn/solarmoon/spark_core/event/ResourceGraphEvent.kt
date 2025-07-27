package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.resource.graph.EdgeType
import net.neoforged.bus.api.Event

/**
 * 资源图变更事件
 * 当 ResourceGraphManager 中的资源节点发生变化时触发
 */
abstract class ResourceGraphEvent : Event() {

    /**
     * 资源节点变更事件
     * @param node 发生变更的资源节点
     * @param changeType 变更类型
     */
    class NodeChange(
        val node: ResourceNode,
        val changeType: ChangeType
    ) : ResourceGraphEvent()

    /**
     * 资源依赖关系变更事件
     * @param source 依赖源节点
     * @param target 依赖目标节点
     * @param edgeType 依赖类型 (HARD, SOFT, OPTIONAL)
     * @param changeType 变更类型
     */
    class DependencyChange(
        val source: ResourceNode,
        val target: ResourceNode,
        val edgeType: EdgeType,
        val changeType: ChangeType
    ) : ResourceGraphEvent()

    /**
     * 资源覆盖关系变更事件
     * @param overrider 覆盖者节点
     * @param overridden 被覆盖者节点
     * @param changeType 变更类型
     */
    class OverrideChange(
        val overrider: ResourceNode,
        val overridden: ResourceNode,
        val changeType: ChangeType
    ) : ResourceGraphEvent()

    /**
     * 资源图重建事件
     * 当整个资源图被重新构建时触发
     */
    object GraphRebuilt : ResourceGraphEvent()

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
}
