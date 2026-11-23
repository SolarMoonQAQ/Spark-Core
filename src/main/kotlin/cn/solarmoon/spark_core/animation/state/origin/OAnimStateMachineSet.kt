package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.AnimResourceKey
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.animation.state.MultiAnimStateMachine
import cn.solarmoon.spark_core.gas.GameplayTagContainer
import cn.solarmoon.spark_core.molang.SparkMolangContext
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateVariableContainer
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class OAnimStateMachineSet(
    val animationControllers: MutableMap<String, OAnimStateMachine>
) {

    /**
     * 递归构建控制器树，只返回不被其他控制器引用的根控制器。
     *
     * <p>未传入 [variables] 或 [tags] 时在本方法内新建实例，递归共享给全部子控；
     * 传入外部容器时则全部子控共享该外部容器。
     *
     * @param animatable 单个动画目标（1:1 本地控制器模式）
     * @param variables  共享变量容器；null 则本方法内新建并递归共享
     * @param tags       共享标签容器；null 则本方法内新建并递归共享
     */
    @JvmOverloads
    fun buildRootMachines(
        animatable: IAnimatable<*>,
        variables: StateVariableContainer? = null,
        tags: GameplayTagContainer? = null
    ): Map<String, AnimStateMachine> {
        // null 时在入口新建，递归共享给全部子控，避免每个子控各自自建
        val sharedVars = variables ?: StateVariableContainer()
        val sharedTags = tags ?: GameplayTagContainer()

        // 第一遍：全部编译为 StateMachineGraph，同时填充 subGraphs
        val graphs = compiledGraphs()

        // 找出根（不被任何 subGraphs 引用的 key）
        val childNames = graphs.values.flatMap { graph ->
            graph.nodeMap.values.flatMap { it.subGraphs.keys }
        }.toSet()
        val rootNames = graphs.keys - childNames

        /** 递归构建子树，子控共享同一 sharedVars/sharedTags */
        fun buildSubtree(graph: StateMachineGraph): AnimStateMachine {
            val children = mutableMapOf<String, StateGraphController>()
            graph.nodeMap.values.forEach { node ->
                node.subGraphs.forEach { (name, subGraph) ->
                    if (name !in children) {
                        children[name] = buildSubtree(subGraph)
                    }
                }
            }
            return AnimStateMachine(graph, animatable, children, sharedVars, sharedTags)
        }

        return rootNames.associateWith { buildSubtree(graphs[it]!!).also { it.start() } }
    }

    /**
     * 中央广播模式：从 JSON 反序列化的动画控制器递归构建 [MultiAnimStateMachine] 树。
     *
     * <p>与 [buildRootMachines] 的区别：
     * <ul>
     *   <li>构建 [MultiAnimStateMachine] 而非 [AnimStateMachine]</li>
     *   <li>全部子控共享同一批 [animTargets] 广播目标</li>
     *   <li>全部子控共享同一 [contextProvider] / [fallbackAnimations] / [builtinAnimations] / [animGroup]</li>
     *   <li>未传入 [variables] 或 [tags] 时在本方法内新建并递归共享</li>
     * </ul>
     *
     * <p>JSON 控制器支持 MoLang 条件转移，以及由 Blockbench 条件 `event.<事件名>` 声明的一次性事件转移；
     * 不支持 [cn.solarmoon.spark_core.state_machine.graph.conditions.CheckVariableCondition]。
     * 需要补充变量条件时，可直接构造 [MultiAnimStateMachine] 并传入同一份 [variables]。
     *
     * @param animTargets        广播目标列表（如机甲的所有 SubPart）
     * @param contextProvider    MoLang 上下文提供者（来自宿主实体）
     * @param fallbackAnimations 实例级默认动画集（素体提供），可为空
     * @param builtinAnimations  Mod 内置动画集，可为空
     * @param animGroup          写入目标动画层
     * @param variables          共享变量容器；null 则本方法内新建并递归共享
     * @param tags               共享标签容器；null 则本方法内新建并递归共享
     * @return 根控制器名 → MultiAnimStateMachine 映射
     */
    @JvmOverloads
    fun buildRootMultiMachines(
        animTargets: List<IAnimatable<*>>,
        contextProvider: () -> SparkMolangContext<*>,
        fallbackAnimations: OAnimationSet? = null,
        builtinAnimations: OAnimationSet? = null,
        animGroup: Int = AnimGroups.AMBIENT,
        variables: StateVariableContainer? = null,
        tags: GameplayTagContainer? = null
    ): Map<String, MultiAnimStateMachine> {
        // null 时在入口新建，递归共享给全部子控
        val sharedVars = variables ?: StateVariableContainer()
        val sharedTags = tags ?: GameplayTagContainer()

        val graphs = compiledGraphs()

        val childNames = graphs.values.flatMap { graph ->
            graph.nodeMap.values.flatMap { it.subGraphs.keys }
        }.toSet()
        val rootNames = graphs.keys - childNames

        /** 递归构建子树，子控同类型，共享同一 sharedVars/sharedTags */
        fun buildSubtree(graph: StateMachineGraph): MultiAnimStateMachine {
            val children = mutableMapOf<String, StateGraphController>()
            graph.nodeMap.values.forEach { node ->
                node.subGraphs.forEach { (name, subGraph) ->
                    if (name !in children) {
                        children[name] = buildSubtree(subGraph)
                    }
                }
            }
            return MultiAnimStateMachine(
                graph, animTargets, contextProvider,
                fallbackAnimations, builtinAnimations, animGroup,
                children, sharedVars, sharedTags
            )
        }

        return rootNames.associateWith { buildSubtree(graphs[it]!!).also { it.start() } }
    }

    /**
     * 本控制器集里由状态按动画名引用的片段名集合。
     *
     * <p>判据与 [compiledGraphs] 相同：状态 `animations` 的键能解析成控制器集里的控制器时是子控制器，
     * 其余键才是动画片段名。动画体据此划分循环片段的归属——命中的片段随控制器状态启停，
     * 未命中的 `loop:true` 片段由动画体自己播放。
     *
     * <p>回环引用的控制器名会进入返回集合：那个名字在被引用处解析不出子控制器，
     * 而回环本身已在 [compiledGraphs] 记录错误日志。
     */
    fun referencedAnimationNames(): Set<String> = buildSet {
        animationControllers.values.forEach { controller ->
            controller.states.values.forEach { state ->
                state.animations.keys.forEach { name ->
                    if (name !in animationControllers) add(name)
                }
            }
        }
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
        /** 已发布的控制器集，键含资源类型。加载器整表替换，不就地改写。 */
        @JvmStatic
        var ORIGINS: MutableMap<AnimResourceKey, OAnimStateMachineSet> = linkedMapOf()

        /** 共享的空控制器集。 */
        @JvmStatic
        val EMPTY: OAnimStateMachineSet = OAnimStateMachineSet(linkedMapOf())

        /**
         * 按资源键查找控制器集；没有对应集合时返回 {@code null}。
         *
         * 返回 {@code null} 而不是每次新建空集合：绑定比较用对象引用，临时空集会让判据永远不成立。
         */
        @JvmStatic
        fun getOrNull(key: AnimResourceKey?): OAnimStateMachineSet? = if (key == null) null else ORIGINS[key]

        /**
         * 整表替换已发布的控制器集。
         *
         * 加载器在全部文件读完后一次调用；资源重载后同一键对应的对象引用会变化，动画体据此重新绑定。
         */
        @JvmStatic
        fun publish(sets: Map<AnimResourceKey, OAnimStateMachineSet>) {
            ORIGINS = LinkedHashMap(sets)
        }

        val CODEC: Codec<OAnimStateMachineSet> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.unboundedMap(Codec.STRING, OAnimStateMachine.CODEC).optionalFieldOf("animation_controllers", mapOf()).forGetter { it.animationControllers }
            ).apply(ins, ::OAnimStateMachineSet)
        }
    }
}
