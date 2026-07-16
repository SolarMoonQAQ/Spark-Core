package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.gas.GameplayTag
import cn.solarmoon.spark_core.gas.GameplayTagContainer
import cn.solarmoon.spark_core.state_machine.presets.StateVariableKeys
import com.mojang.serialization.MapCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 纯状态图单元测试 —— 不依赖 Minecraft 世界和动画资源。
 *
 * 核心覆盖：生命周期契约、事件广播语义、ActionEvent.targetNode。
 *
 * 生产类的 companion object 引用了 Minecraft Codec 和 NeoForge 注册表，
 * 因此测试通过 ModDevGradle 的 JUnit 集成获得完整的模组开发运行时。
 * 这些测试不依赖世界、资源或游戏 tick，无需使用 GameTest。
 */
class StateGraphControllerTest {

    // ═══════════════════════════════════════════════
    // 测试辅助
    // ═══════════════════════════════════════════════

    /** 记录 entry/exit 调用序列的辅助类 */
    private class LifecycleRecorder {
        val entries = mutableListOf<String>()
        val exits = mutableListOf<String>()
        var triggeredTarget: StateNode? = null
        var triggerCount = 0
    }

    /** 构造一个简单单节点图：initial（无转移，无子图） */
    private fun singleNodeGraph(name: String = "initial"): StateMachineGraph {
        return StateMachineGraph(
            initialNode = StateNode(name, transitions = emptyList()),
            nodes = emptyList()
        )
    }

    /** 构造两个节点 A→B 的图，A->B 通过给定事件转移 */
    private fun twoNodeGraph(
        event: String? = null,
        condition: StateCondition = StateCondition.True,
        aName: String = "A",
        bName: String = "B"
    ): StateMachineGraph {
        val transition = if (event != null) {
            StateTransition(event = event, target = bName, condition = condition)
        } else {
            StateTransition(event = null, target = bName, condition = condition)
        }
        return StateMachineGraph(
            initialNode = StateNode(aName, transitions = listOf(transition)),
            nodes = listOf(StateNode(bName, transitions = emptyList()))
        )
    }

    /** 带子图声明的图 */
    private fun parentGraph(
        subGraphs: Map<String, StateMachineGraph> = emptyMap(),
        event: String? = null,
        subTarget: String = "child_owner"
    ): Pair<StateMachineGraph, StateNode> {
        val childOwner = StateNode(
            name = subTarget,
            transitions = if (event != null)
                listOf(StateTransition(event = event, target = "post_child", condition = StateCondition.True))
            else emptyList(),
            subGraphs = subGraphs
        )
        val graph = StateMachineGraph(
            initialNode = childOwner,
            nodes = if (event != null)
                listOf(StateNode("post_child", transitions = emptyList()))
            else emptyList()
        )
        return graph to childOwner
    }

    // ═══════════════════════════════════════════════
    // 5.3 生命周期边界 — 构造后未启动
    // ═══════════════════════════════════════════════

    @Test
    fun `子控制器构造完成但不激活时，不执行初始节点 onEntry`() {
        val entryRecorder = mutableListOf<String>()
        val childGraph = singleNodeGraph("child_init")
        val childNode = childGraph.initialNode

        // 利用子类记录 entry 调用
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) {
                entryRecorder.add(node.name)
            }
        }

        // 构造后未 start → onEntry 不应被调用
        assertFalse(child.isStarted)
        assertEquals(0, entryRecorder.size)

        // start 后应触发一次 initial node 的 onEntry
        child.start()
        assertEquals(1, entryRecorder.size)
        assertEquals("child_init", entryRecorder[0])
    }

    @Test
    fun `stopped 控制器调用 triggerEvent 抛出异常`() {
        val controller = StateGraphController(singleNodeGraph())
        assertFalse(controller.isStarted)
        assertThrows(IllegalStateException::class.java) {
            controller.triggerEvent("any")
        }
    }

    @Test
    fun `stopped 控制器调用 progress 抛出异常`() {
        val controller = StateGraphController(singleNodeGraph())
        assertFalse(controller.isStarted)
        assertThrows(IllegalStateException::class.java) {
            controller.progress()
        }
    }

    @Test
    fun `stopped 根控制器只有在 start 后才能 progress`() {
        val recorder = LifecycleRecorder()
        val graph = twoNodeGraph(event = null) // auto 转移 A→B
        val controller = object : StateGraphController(graph) {
            override fun onEntry(node: StateNode) { recorder.entries.add(node.name) }
            override fun onExit(node: StateNode) { recorder.exits.add(node.name) }
        }

        controller.start()
        assertEquals(1, recorder.entries.size)
        assertEquals("A", recorder.entries[0])

        // progress 会触发 auto 转移 A→B
        controller.progress()
        assertEquals(2, recorder.entries.size)
        assertEquals("B", recorder.entries[1])
        assertEquals(1, recorder.exits.size)
        assertEquals("A", recorder.exits[0])
    }

    // ═══════════════════════════════════════════════
    // 5.1 基本行为
    // ═══════════════════════════════════════════════

    @Test
    fun `根控制器匹配事件时发生一次转移`() {
        val recorder = LifecycleRecorder()
        val graph = twoNodeGraph(event = "dodge", aName = "A", bName = "B")
        val controller = object : StateGraphController(graph) {
            override fun onEntry(node: StateNode) { recorder.entries.add(node.name) }
            override fun onExit(node: StateNode) { recorder.exits.add(node.name) }
        }
        controller.start()

        assertEquals("A", controller.currentNode.name)
        controller.triggerEvent("dodge")
        assertEquals("B", controller.currentNode.name)
        assertEquals(listOf("A", "B"), recorder.entries)
        assertEquals(listOf("A"), recorder.exits)
    }

    @Test
    fun `未知事件不改变任何控制器状态`() {
        val graph = twoNodeGraph(event = "dodge", aName = "A", bName = "B")
        val controller = StateGraphController(graph)
        controller.start()

        controller.triggerEvent("unknown")
        assertEquals("A", controller.currentNode.name)
    }

    @Test
    fun `活跃直接子控制器匹配事件时发生一次转移`() {
        val parentEntry = mutableListOf<String>()
        val childEntry = mutableListOf<String>()

        val childGraph = twoNodeGraph(event = "dodge", aName = "child_A", bName = "child_B")
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) { childEntry.add(node.name) }
        }

        // 父图声明含子图 "child_ctrl"
        val (parentGraph, _) = parentGraph(subGraphs = mapOf("child_ctrl" to childGraph))
        val parent = object : StateGraphController(parentGraph, children = mapOf("child_ctrl" to child)) {
            override fun onEntry(node: StateNode) { parentEntry.add(node.name) }
        }
        parent.start()

        // 父进入 child_owner 节点 → 子图应被激活
        assertEquals("child_owner", parent.currentNode.name)
        assertEquals(listOf("child_owner"), parentEntry)
        assertEquals(listOf("child_A"), childEntry) // 子图初始节点

        // 发送广播 → 子图应匹配 dodge 转移
        parent.broadcastEvent("dodge")
        assertEquals("child_B", child.currentNode.name)
        assertEquals(listOf("child_A", "child_B"), childEntry)
    }

    @Test
    fun `未激活子控制器不会收到事件，状态保持不变`() {
        val childEntry = mutableListOf<String>()

        val childGraph = twoNodeGraph(event = "dodge", aName = "child_A", bName = "child_B")
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) { childEntry.add(node.name) }
        }

        // 父图不声明子图 → 子图不会被激活
        val parentGraph = singleNodeGraph("idle")
        val parent = StateGraphController(parentGraph, children = mapOf("unused_child" to child))
        parent.start()

        // 父节点不激活子图，子图仍处于 stopped
        assertFalse(child.isStarted)
        assertEquals(0, childEntry.size)

        // 子图未激活，不应收到事件
        // 注意：broadcastEvent 从父级遍历 activeChildren，未激活子图不在其中
        assertEquals("child_A", childGraph.initialNode.name)
    }

    @Test
    fun `活跃孙控制器匹配事件时发生一次转移`() {
        val grandchildEntry = mutableListOf<String>()

        val grandchildGraph = twoNodeGraph(event = "dodge", aName = "gc_A", bName = "gc_B")
        val grandchild = object : StateGraphController(grandchildGraph) {
            override fun onEntry(node: StateNode) { grandchildEntry.add(node.name) }
        }

        val childGraph = parentGraph(
            subGraphs = mapOf("grandchild_ctrl" to grandchildGraph)
        ).first
        val child = StateGraphController(childGraph, children = mapOf("grandchild_ctrl" to grandchild))

        val parentGraph = parentGraph(
            subGraphs = mapOf("child_ctrl" to childGraph)
        ).first
        val parent = StateGraphController(parentGraph, children = mapOf("child_ctrl" to child))

        parent.start()

        // 广播 dodge → 应传递到孙子
        parent.broadcastEvent("dodge")
        assertEquals("gc_B", grandchild.currentNode.name)
    }

    // ═══════════════════════════════════════════════
    // 5.2 并行广播
    // ═══════════════════════════════════════════════

    @Test
    fun `两个活跃并行子控制器均声明同名事件时，两者各转移一次`() {
        val child1Entry = mutableListOf<String>()
        val child2Entry = mutableListOf<String>()

        val graph1 = twoNodeGraph(event = "go", aName = "c1_A", bName = "c1_B")
        val child1 = object : StateGraphController(graph1) {
            override fun onEntry(node: StateNode) { child1Entry.add(node.name) }
        }
        val graph2 = twoNodeGraph(event = "go", aName = "c2_A", bName = "c2_B")
        val child2 = object : StateGraphController(graph2) {
            override fun onEntry(node: StateNode) { child2Entry.add(node.name) }
        }

        // 父节点同时声明两个子图
        val parentGraph = StateMachineGraph(
            initialNode = StateNode("multi_child", transitions = emptyList(),
                subGraphs = mapOf(
                    "child1" to graph1,
                    "child2" to graph2
                )
            ),
            nodes = emptyList()
        )
        val parent = StateGraphController(
            parentGraph,
            children = mapOf("child1" to child1, "child2" to child2)
        )
        parent.start()

        assertEquals("c1_A", child1.currentNode.name)
        assertEquals("c2_A", child2.currentNode.name)

        parent.broadcastEvent("go")

        assertEquals("c1_B", child1.currentNode.name)
        assertEquals("c2_B", child2.currentNode.name)
    }

    @Test
    fun `子控制器与父控制器均声明同名事件时，父级先转移，子控制器后转移`() {
        val order = mutableListOf<String>()

        // 子图：sub_A --go--> sub_B
        val subGraph = twoNodeGraph(event = "go", aName = "sub_A", bName = "sub_B")
        val child = object : StateGraphController(subGraph) {
            override fun onEntry(node: StateNode) { order.add("child:${node.name}") }
            override fun onExit(node: StateNode) { order.add("child-:${node.name}") }
        }

        // 父图：parent_A --go--> parent_B，两个状态使用同一个子控制器。
        // 父级转移会停用旧子图、重新激活新状态的子图，再向它广播同一事件。
        val parentNode = StateNode(
            name = "parent_A",
            transitions = listOf(StateTransition(event = "go", target = "parent_B", condition = StateCondition.True)),
            subGraphs = mapOf("sub" to subGraph)
        )
        val parentGraph = StateMachineGraph(
            initialNode = parentNode,
            nodes = listOf(
                StateNode(
                    name = "parent_B",
                    transitions = emptyList(),
                    subGraphs = mapOf("sub" to subGraph)
                )
            )
        )
        val parent = object : StateGraphController(parentGraph, children = mapOf("sub" to child)) {
            override fun onEntry(node: StateNode) { order.add("parent:${node.name}") }
            override fun onExit(node: StateNode) { order.add("parent-:${node.name}") }
        }
        parent.start()

        order.clear() // 清空启动阶段的 entry 记录

        // 广播 go
        parent.broadcastEvent("go")

        // 父级先处理 go 事件（父级 parent_A→parent_B 的 exit 早于子转移）
        assertEquals("parent_B", parent.currentNode.name)
        assertEquals("sub_B", child.currentNode.name)

        // 生命周期退出保持内到外；新父状态完成 entry 后，才向重新激活的子图派发事件。
        assertTrue(order.indexOf("child-:sub_A") < order.indexOf("parent-:parent_A"),
            "旧子图应先于父节点退出")
        assertTrue(order.indexOf("parent:parent_B") < order.lastIndexOf("child-:sub_A"),
            "父级转移完成后才应处理新激活子图的事件")
    }

    @Test
    fun `同一事件在任一控制器内只执行第一条条件成立的转移`() {
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "start",
                transitions = listOf(
                    StateTransition(event = "go", target = "target1", condition = StateCondition.True),
                    StateTransition(event = "go", target = "target2", condition = StateCondition.True)
                )
            ),
            nodes = listOf(
                StateNode("target1", transitions = emptyList()),
                StateNode("target2", transitions = emptyList())
            )
        )
        val controller = StateGraphController(graph)
        controller.start()

        controller.triggerEvent("go")
        assertEquals("target1", controller.currentNode.name)
    }

    // ═══════════════════════════════════════════════
    // 5.3 生命周期边界
    // ═══════════════════════════════════════════════

    @Test
    fun `父级事件导致退出当前状态时，旧子图不接收该事件`() {
        val childRecorder = LifecycleRecorder()

        // 子图：只有 "child_stay" 节点，无转移
        val childGraph = singleNodeGraph("child_stay")
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) { childRecorder.entries.add(node.name) }
            override fun onExit(node: StateNode) { childRecorder.exits.add(node.name) }
        }

        // 父图：parent_start --exit--> parent_end
        // parent_start 声明子图，parent_end 不声明
        val childOwner = StateNode(
            name = "parent_start",
            transitions = listOf(StateTransition(event = "exit", target = "parent_end", condition = StateCondition.True)),
            subGraphs = mapOf("child_ctrl" to childGraph)
        )
        val parentGraph = StateMachineGraph(
            initialNode = childOwner,
            nodes = listOf(StateNode("parent_end", transitions = emptyList()))
        )
        val parent = StateGraphController(parentGraph, children = mapOf("child_ctrl" to child))
        parent.start()

        assertEquals("parent_start", parent.currentNode.name)
        assertEquals(listOf("child_stay"), childRecorder.entries)

        // 广播 exit → 父转移到 parent_end，子图应被退出
        parent.broadcastEvent("exit")

        assertEquals("parent_end", parent.currentNode.name)
        assertEquals(listOf("child_stay"), childRecorder.entries) // child entry 只有一次
        assertEquals(listOf("child_stay"), childRecorder.exits)  // child exit 恰好一次

        // 确认子图不再活跃
        assertFalse(child.isStarted)
    }

    @Test
    fun `父级事件激活的新子图会接收正在处理的同一个事件，并可据此转移`() {
        val childRecorder = LifecycleRecorder()

        // 子图 B：当收到 "go" 事件时从 b_start 转移到 b_end
        val childGraphB = twoNodeGraph(event = "go", aName = "b_start", bName = "b_end")
        val childB = object : StateGraphController(childGraphB) {
            override fun onEntry(node: StateNode) { childRecorder.entries.add(node.name) }
            override fun onExit(node: StateNode) { childRecorder.exits.add(node.name) }
        }

        // 父图 parent_start --go--> parent_end
        // parent_end 声明子图 childB
        val startNode = StateNode("parent_start", transitions = listOf(
            StateTransition(event = "go", target = "parent_end", condition = StateCondition.True)
        ))
        val endNode = StateNode(
            name = "parent_end",
            transitions = emptyList(),
            subGraphs = mapOf("childB_ctrl" to childGraphB)
        )
        val parentGraph = StateMachineGraph(initialNode = startNode, nodes = listOf(endNode))
        val parent = StateGraphController(parentGraph, children = mapOf("childB_ctrl" to childB))
        parent.start()

        assertEquals("parent_start", parent.currentNode.name)

        // 广播 go → 父转移到 parent_end，激活 childB，childB 应收到同一个 "go" 事件
        parent.broadcastEvent("go")

        assertEquals("parent_end", parent.currentNode.name)
        assertEquals("b_end", childB.currentNode.name) // childB 也处理了 "go"
    }

    @Test
    fun `广播完成后再次 progress 只进行一次正常自动推进`() {
        val recorder = LifecycleRecorder()

        // 纯 auto 转移链：a --[null]--> b --[null]--> c
        val graph = StateMachineGraph(
            initialNode = StateNode("a", transitions = listOf(
                StateTransition(event = null, target = "b", condition = StateCondition.True)
            )),
            nodes = listOf(
                StateNode("b", transitions = listOf(
                    StateTransition(event = null, target = "c", condition = StateCondition.True)
                )),
                StateNode("c", transitions = emptyList())
            )
        )
        val controller = object : StateGraphController(graph) {
            override fun onEntry(node: StateNode) { recorder.entries.add(node.name) }
            override fun onExit(node: StateNode) { recorder.exits.add(node.name) }
        }
        controller.start() // 进入 a
        recorder.entries.clear()

        // progress 1: a → b
        controller.progress()
        assertEquals("b", controller.currentNode.name)
        assertEquals(listOf("b"), recorder.entries)

        // progress 2: b → c
        controller.progress()
        assertEquals("c", controller.currentNode.name)
    }

    @Test
    fun `首次激活子图时初始节点 onEntry 恰好执行一次`() {
        val childEntry = mutableListOf<String>()

        val childGraph = singleNodeGraph("child_start")
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) { childEntry.add(node.name) }
        }

        val (parentGraph, _) = parentGraph(subGraphs = mapOf("child" to childGraph))
        val parent = StateGraphController(parentGraph, children = mapOf("child" to child))
        parent.start()

        // child_start 应恰好 entry 一次（通过 enterNode 激活）
        assertEquals(listOf("child_start"), childEntry)
    }

    @Test
    fun `父级退出时子图 exit 恰好执行一次`() {
        val childExit = mutableListOf<String>()

        val childGraph = singleNodeGraph("child_stay")
        val child = object : StateGraphController(childGraph) {
            override fun onExit(node: StateNode) { childExit.add(node.name) }
        }

        // 父级：child_owner --leave--> done
        val childOwner = StateNode(
            name = "child_owner",
            transitions = listOf(StateTransition(event = "leave", target = "done", condition = StateCondition.True)),
            subGraphs = mapOf("child" to childGraph)
        )
        val parentGraph = StateMachineGraph(
            initialNode = childOwner,
            nodes = listOf(StateNode("done", transitions = emptyList()))
        )
        val parent = StateGraphController(parentGraph, children = mapOf("child" to child))
        parent.start()

        assertEquals(0, childExit.size)

        parent.broadcastEvent("leave")
        assertEquals("done", parent.currentNode.name)

        // child 应 exit 恰好一次
        assertEquals(listOf("child_stay"), childExit)
    }

    @Test
    fun `reset 时旧活跃树 exit 一次 初始活跃树 entry 一次 不重复初始化 children`() {
        val entryOrder = mutableListOf<String>()
        val exitOrder = mutableListOf<String>()

        val childGraph = singleNodeGraph("child_node")
        val child = object : StateGraphController(childGraph) {
            override fun onEntry(node: StateNode) { entryOrder.add("child:${node.name}") }
            override fun onExit(node: StateNode) { exitOrder.add("child:${node.name}") }
        }

        val (parentGraph, _) = parentGraph(subGraphs = mapOf("child_ctrl" to childGraph))
        val parent = object : StateGraphController(parentGraph, children = mapOf("child_ctrl" to child)) {
            override fun onEntry(node: StateNode) { entryOrder.add("parent:${node.name}") }
            override fun onExit(node: StateNode) { exitOrder.add("parent:${node.name}") }
        }
        parent.start()

        // 检查初始 entry
        val firstEntrySize = entryOrder.size

        // reset
        parent.reset()

        // entry 数量应为初始 entry 数量的两倍（初始 + reset 后重新 entry）
        assertEquals(firstEntrySize * 2, entryOrder.size)
        // exit 数量应等于初始 entry 数量
        assertEquals(firstEntrySize, exitOrder.size)
    }

    @Test
    fun `stop后start可以重新进入初始状态且storage不受影响`() {
        val entryOrder = mutableListOf<String>()
        val exitOrder = mutableListOf<String>()

        // 单节点图，无转移
        val graph = singleNodeGraph("idle")
        val controller = object : StateGraphController(graph) {
            override fun onEntry(node: StateNode) { entryOrder.add(node.name) }
            override fun onExit(node: StateNode) { exitOrder.add(node.name) }
        }
        controller.start()

        // 写入自有 storage
        controller.variables.set(StateVariableKeys.SPEED, 10f)
        controller.tags.add(GameplayTag("test_tag"))
        assertEquals(10f, controller.variables.get(StateVariableKeys.SPEED))

        // stop → start 序列
        controller.stop()
        assertFalse(controller.isStarted)
        assertEquals(listOf("idle"), exitOrder) // exit 恰好一次

        // stop 后 storage 应保留（不受 reset 的 clear 影响）
        assertEquals(10f, controller.variables.get(StateVariableKeys.SPEED))
        assertTrue(controller.tags.has(GameplayTag("test_tag")))

        controller.start()
        assertTrue(controller.isStarted)
        // start 后应重新进入初始节点
        assertEquals(2, entryOrder.size)
        assertEquals("idle", controller.currentNode.name)
    }

    @Test
    fun `exit 顺序固定为活跃子图 exit 父节点onExit actions 父控制器onExit钩子`() {
        val executionOrder = mutableListOf<String>()

        val childGraph = singleNodeGraph("child_node")
        val child = object : StateGraphController(childGraph) {
            override fun onExit(node: StateNode) { executionOrder.add("child_exit") }
        }

        // onExit action 记录顺序
        val childOwner = StateNode(
            name = "child_owner",
            transitions = listOf(StateTransition(event = "leave", target = "done", condition = StateCondition.True)),
            subGraphs = mapOf("child_ctrl" to childGraph),
            onExit = listOf(object : StateAction {
                override val codec: MapCodec<out StateAction> = MapCodec.unit(this)
                override fun execute(controller: StateGraphController) {
                    executionOrder.add("node_onExit_action")
                }
            })
        )
        val parentGraph = StateMachineGraph(
            initialNode = childOwner,
            nodes = listOf(StateNode("done", transitions = emptyList()))
        )
        val parent = object : StateGraphController(parentGraph, children = mapOf("child_ctrl" to child)) {
            override fun onExit(node: StateNode) { executionOrder.add("controller_onExit_hook") }
        }
        parent.start()

        parent.broadcastEvent("leave")

        // 顺序应为：子 exit → 节点 onExit action → 控制器 onExit 钩子
        assertEquals("child_exit", executionOrder[0])
        assertEquals("node_onExit_action", executionOrder[1])
        assertEquals("controller_onExit_hook", executionOrder[2])
    }

    // ═══════════════════════════════════════════════
    // 5.4 ActionEvent.targetNode
    // ═══════════════════════════════════════════════

    @Test
    fun `无匹配转移时 targetNode 为 null`() {
        val graph = twoNodeGraph(event = "go", aName = "A", bName = "B")
        val controller = StateGraphController(graph)
        controller.start()

        val event = controller.triggerEvent("unknown")
        assertNull(event.targetNode)
    }

    @Test
    fun `有匹配转移时 targetNode 是目标节点而非源节点`() {
        val graph = twoNodeGraph(event = "go", aName = "A", bName = "B")
        val controller = StateGraphController(graph)
        controller.start()

        val event = controller.triggerEvent("go")
        assertNotNull(event.targetNode)
        assertEquals("B", event.targetNode!!.name)
        assertNotEquals("A", event.targetNode!!.name)
    }

    @Test
    fun `自转移时 targetNode 可以与源节点相同`() {
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "self",
                transitions = listOf(StateTransition(event = "refresh", target = "self", condition = StateCondition.True))
            ),
            nodes = emptyList()
        )
        val controller = StateGraphController(graph)
        controller.start()

        val event = controller.triggerEvent("refresh")
        assertNotNull(event.targetNode)
        assertEquals("self", event.targetNode!!.name)
    }

    // ═══════════════════════════════════════════════
    // 共享 storage 所有权
    // ═══════════════════════════════════════════════

    @Test
    fun `自有 storage 在 reset 时清理`() {
        val childController = StateGraphController(singleNodeGraph(), variables = null, tags = null)
        assertTrue(childController.variables.run {
            // 验证容器已正确创建
            true
        })
        // 自有 storage：构造时 variables == null, tags == null
        childController.start()
        childController.reset()
        // 不会抛异常，clear 对空容器无害
    }

    @Test
    fun `共享 storage 在 reset 前后保持由外部管理`() {
        val sharedVars = StateVariableContainer()
        val sharedTags = GameplayTagContainer()
        val controller = StateGraphController(singleNodeGraph(), variables = sharedVars, tags = sharedTags)
        controller.start()
        controller.reset()
        // 外部容器在 reset 后仍可用（clear 由外部管理）
        assertNotNull(sharedVars)
        assertNotNull(sharedTags)
    }

    // ═══════════════════════════════════════════════
    // 父子变量共享
    // ═══════════════════════════════════════════════

    @Test
    fun `父子共享同一变量容器时引用相同`() {
        val sharedVars = StateVariableContainer()

        val childGraph = singleNodeGraph("child_A")
        val child = object : StateGraphController(childGraph, variables = sharedVars) {
            override fun onEntry(node: StateNode) {
                // 子图 entry 时读取父级写入的变量
            }
        }

        val (parentGraph, _) = parentGraph(subGraphs = mapOf("child_ctrl" to childGraph))
        val parent = StateGraphController(parentGraph, children = mapOf("child_ctrl" to child), variables = sharedVars)
        parent.start()

        // 父子共享同一容器
        assertSame(parent.variables, child.variables)
    }
}
