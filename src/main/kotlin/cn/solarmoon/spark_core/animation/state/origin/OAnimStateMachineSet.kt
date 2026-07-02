package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

data class OAnimStateMachineSet(
    val animationControllers: MutableMap<String, OAnimStateMachine>
) {

    /**
     * 递归构建控制器树，只返回不被其他控制器引用的根控制器。
     */
    fun buildRootMachines(animatable: IAnimatable<*>): Map<String, AnimStateMachine> {
        // 第一遍：全部编译为 StateMachineGraph，同时填充 subGraphs
        val graphs = compiledGraphs()

        // 找出根（不被任何 subGraphs 引用的 key）
        val childNames = graphs.values.flatMap { graph ->
            graph.nodeMap.values.flatMap { it.subGraphs.keys }
        }.toSet()
        val rootNames = graphs.keys - childNames

        /** 递归构建子树 */
        fun buildSubtree(graph: StateMachineGraph): AnimStateMachine {
            val children = mutableMapOf<String, StateGraphController>()
            // 收集 graph 中所有 state 引用的子图 → 递归构建
            graph.nodeMap.values.forEach { node ->
                node.subGraphs.forEach { (name, subGraph) ->
                    if (name !in children) {
                        children[name] = buildSubtree(subGraph)
                    }
                }
            }
            return AnimStateMachine(graph, animatable, children)
        }

        return rootNames.associateWith { buildSubtree(graphs[it]!!) }
    }

    /**
     * 编译全部控制器为 [StateMachineGraph]（带回环检测）。
     */
    private fun compiledGraphs(): Map<String, StateMachineGraph> {
        val resolved = mutableMapOf<String, StateMachineGraph?>()
        val resolving = mutableSetOf<String>()

        fun resolve(name: String): StateMachineGraph? {
            if (name in resolving) {
                SparkCore.LOGGER.error("动画控制器回环引用: {} ← {}",
                    resolving.joinToString(" ← "), name)
                return null
            }
            resolved[name]?.let { return it }
            val src = animationControllers[name] ?: return null
            resolving.add(name)
            val graph = src.toStateMachineGraph(::resolve)  // 递归编译（填充 subGraphs）
            resolving.remove(name)
            resolved[name] = graph
            return graph
        }

        animationControllers.keys.forEach { resolve(it) }
        return resolved.filterValues { it != null }.mapValues { it.value!! }
    }

    companion object {
        val ORIGINS = mutableMapOf<ResourceLocation, OAnimStateMachineSet>()

        fun getOrEmpty(modelIndex: ResourceLocation?) = ORIGINS[modelIndex] ?: OAnimStateMachineSet(mutableMapOf())

        val CODEC: Codec<OAnimStateMachineSet> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.unboundedMap(Codec.STRING, OAnimStateMachine.CODEC).optionalFieldOf("animation_controllers", mapOf()).forGetter { it.animationControllers }
            ).apply(ins, ::OAnimStateMachineSet)
        }
    }
}
