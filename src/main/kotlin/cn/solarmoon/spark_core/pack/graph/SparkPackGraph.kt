package cn.solarmoon.spark_core.pack.graph

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

class SparkPackGraph(
    val originNodes: MutableMap<ResourceLocation, SparkPackage> = mutableMapOf()
) {

    fun addNode(node: SparkPackage) {
        originNodes[node.meta.id] = node
    }

    fun getNode(id: ResourceLocation) = originNodes[id]

    /**
     * 对当前资源图进行依赖检查并排序并输出排序后的顺序列表
     */
    fun resolveLoadOrder(): List<SparkPackage> {
        val result = mutableListOf<SparkPackage>()
        val visited = mutableSetOf<ResourceLocation>()

        fun dfs(node: SparkPackage) {
            if (node.meta.id in visited) return

            DependencyType.entries.forEach { dType ->
                node.meta.dependencyMap[dType]?.forEach {
                    when(dType) {
                        DependencyType.HARD -> if (getNode(it) == null) throw IllegalStateException("${node.meta.id} 缺少硬依赖: $it")
                        DependencyType.CONFLICT -> if (getNode(it) != null) throw IllegalStateException("${node.meta.id} 存在冲突依赖: $it")
                        DependencyType.SOFT -> if (getNode(it) == null) SparkCore.LOGGER.warn("${node.meta.id} 建议装载依赖: $it")
                        else -> {}
                    }
                }
            }

            // 先加载依赖
            node.meta.dependencies.forEach { getNode(it.id)?.let { dfs(it) } }
            visited += node.meta.id
            result += node
        }

        originNodes.values.forEach { dfs(it) }
        return result
    }

    companion object {
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(::LinkedHashMap, ResourceLocation.STREAM_CODEC, SparkPackage.STREAM_CODEC), SparkPackGraph::originNodes,
            ::SparkPackGraph
        )
    }

}
