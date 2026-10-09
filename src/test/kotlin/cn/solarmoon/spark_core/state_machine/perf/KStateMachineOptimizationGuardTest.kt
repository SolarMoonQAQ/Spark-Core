package cn.solarmoon.spark_core.state_machine.perf

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.graph.StateTransition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.findState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.InternalStateMachine
import ru.nsk.kstatemachine.statemachine.ProcessingResult
import ru.nsk.kstatemachine.statemachine.buildCreationArguments
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onTransitionComplete
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.transition.Transition
import ru.nsk.kstatemachine.transition.TransitionParams
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.targetState

/**
 * 针对事件解析优化的**行为回归测试**。
 *
 * 性能优化极易在无感知的情况下改变语义。这些测试锁定被改动代码的可观察行为：
 *
 * | 优化点 | 源码位置 | 本文件中的守卫 |
 * |---|---|---|
 * | `findUniqueResolvedTransition` 单遍扫描替代 filter/map/filter | `state/InternalState.kt` | [每个匹配转移的 direction 只求值一次]、[多个转移匹配时仍然抛异常]、[允许多转移匹配时取第一条] |
 * | 无转移状态的提前返回 | `state/InternalState.kt` | [无转移的状态不解析出任何转移]、[doNotThrow 模式下中间层无转移时仍能命中深层转移] |
 * | 通知与监听器语义（原有守卫） | `statemachine/StateMachineImpl.kt`、`state/StateListener.kt` | [有监听器时 onComplete 仍收到正确活跃状态]、[无监听器时转移语义不变]、[监听器静默段内转移不通知外部] |
 *
 * 任何一条失败都说明优化破坏了向后兼容性，必须回退对应改动。
 *
 * 2026-10-10 复核：回退了 5 处实测收益为 0 的改动（`BaseStateImpl` 快路径、三处监听器防拷贝、
 * 跳过 `activeStates()`），只保留 `InternalState` 的单遍扫描；`BaseStateImpl` 快路径的
 * `doNotThrowOnMultipleTransitionsMatch` 分支会静默丢弃深层转移，为此新增了对应守卫。
 */
class KStateMachineOptimizationGuardTest {

    // ══════════════════════════════════════════════════════════
    // direction producer 求值次数
    // ══════════════════════════════════════════════════════════

    /**
     * 优化后 `findUniqueResolvedTransition` 只求值一次 direction。
     * 原实现 `transitions.map { it to it.produceTargetStateDirection(policy) }` 同样是每个匹配转移一次，
     * 但中间会先 `filter` 出所有匹配转移 —— 本测试确保重构没有变成「重复求值」或「漏求值」。
     */
    @Test
    fun `每个匹配转移的 direction 只求值一次`() {
        var evaluations = 0
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            // 自转移到 a：事件前后活跃状态都是 a，便于重复发同一个事件。
            // EXTERNAL 类型保证不会被 setCurrentState 的同状态短路吃掉求值。
            a.transitionConditionally<ProbeEvent>("probe") {
                type = ru.nsk.kstatemachine.transition.TransitionType.EXTERNAL
                direction = {
                    evaluations++
                    targetState(a)
                }
            }
        }
        machine.startBlocking()

        machine.processEventBlocking(ProbeEvent)
        assertEquals(1, evaluations, "一次事件对一条匹配转移：direction producer 应恰好求值一次")

        machine.processEventBlocking(ProbeEvent)
        assertEquals(2, evaluations, "第二次事件应再求值一次（不是零次，也不是两次）")
    }

    /**
     * LOCAL 自转移（target == 当前状态的父级当前状态）在 `setCurrentState` 中会被短路，
     * 但 **direction producer 仍然会被求值** —— 优化前后一致。
     * 这条行为容易被「顺手优化」掉（比如看到 target 没变就提前 return），必须锁住。
     */
    @Test
    fun `LOCAL 自转移仍然求值 direction`() {
        var evaluations = 0
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            a.transitionConditionally<ProbeEvent>("self") {
                direction = {
                    evaluations++
                    targetState(a)
                }
            }
        }
        machine.startBlocking()

        machine.processEventBlocking(ProbeEvent)
        machine.processEventBlocking(ProbeEvent)
        assertEquals(2, evaluations, "自转移也必须每次求值 direction（求值在 setCurrentState 短路之前）")
    }

    /** 条件转移到 `noTransition()` 时不应产生转移，且不应破坏解析结果 */
    @Test
    fun `noTransition 的转移被正确跳过`() {
        var reached = false
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<ProbeEvent>("never") { direction = { noTransition() } }
            a.transitionConditionally<ProbeEvent>("always") { direction = { targetState(b) } }
            b.onEntry { reached = true }
        }
        machine.startBlocking()

        machine.processEventBlocking(ProbeEvent)
        assertTrue(reached, "noTransition 的转移应被跳过，命中第二条转移")
    }

    /**
     * 关键回归点：**未匹配事件**的转移，其 direction 求值器不应被调用。
     *
     * 原实现先 `filter { it.isMatchingEvent(event) }` 再 `map { produceTargetStateDirection }`，
     * 语义上本来就不会对未匹配的转移求值。优化把三步合并成单遍循环，
     * 必须保证「不匹配 → continue → 不调用求值器」这一顺序不被破坏
     * （否则会意外执行用户的业务逻辑，让条件副作用被触发）。
     */
    @Test
    fun `未匹配事件的转移不求值 direction`() {
        var matchedEvaluations = 0
        var unmatchedEvaluations = 0
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            val c = state("c")
            a.transitionConditionally<OtherEvent>("other") {
                direction = { unmatchedEvaluations++; targetState(c) }
            }
            a.transitionConditionally<ProbeEvent>("probe") {
                direction = { matchedEvaluations++; targetState(b) }
            }
        }
        machine.startBlocking()

        // 事件落在 ProbeEvent 上：只有 probe 的求值器允许被调用
        machine.processEventBlocking(ProbeEvent)
        assertEquals(1, matchedEvaluations, "匹配的转移应求值一次")
        assertEquals(0, unmatchedEvaluations, "不匹配的转移不应被求值")

        // 此时当前活跃状态是 b —— 从 b 出发没有 OtherEvent 转移 → IGNORED
        val result = machine.processEventBlocking(OtherEvent)
        assertEquals(ProcessingResult.IGNORED, result)
        assertEquals(0, unmatchedEvaluations, "b 状态下没有 OtherEvent 转移，不应求值")
        assertEquals(1, matchedEvaluations, "matched 求值次数不应再增长")
    }

    /** 事件全不匹配时，应返回 IGNORED 且不产生任何求值副作用 */
    @Test
    fun `全不匹配时不求值任何 direction 且返回 IGNORED`() {
        var evaluations = 0
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<OtherEvent>("o1") { direction = { evaluations++; targetState(b) } }
            a.transitionConditionally<OtherEvent>("o2") { direction = { evaluations++; targetState(b) } }
        }
        machine.startBlocking()

        val result = machine.processEventBlocking(ProbeEvent)
        assertEquals(ProcessingResult.IGNORED, result)
        assertEquals(0, evaluations, "事件不匹配时不应求值任何 direction")
    }

    /**
     * 多个转移同时匹配时默认仍然抛异常。
     * 注意：单遍扫描在**第二条**命中的转移处就抛出，不再像原实现那样先把所有匹配转移求值完；
     * 异常类型与信息格式保持不变（信息里仍然带冲突的转移）。
     */
    @Test
    fun `多个转移匹配时仍然抛异常`() {
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<ProbeEvent>("t1") { direction = { targetState(b) } }
            a.transitionConditionally<ProbeEvent>("t2") { direction = { targetState(b) } }
        }
        machine.startBlocking()

        val e = assertThrows<IllegalStateException> { machine.processEventBlocking(ProbeEvent) }
        assertTrue(e.message!!.contains("Multiple transitions match"), "异常信息应说明多转移匹配：${e.message}")
    }

    /** `doNotThrowOnMultipleTransitionsMatch = true` 时应取第一条 */
    @Test
    fun `允许多转移匹配时取第一条`() {
        var firstHit = false
        var secondHit = false
        val machine = createStdLibStateMachine(
            start = false,
            creationArguments = buildCreationArguments {
                doNotThrowOnMultipleTransitionsMatch = true
            }
        ) {
            val a = initialState("a")
            val b = state("b")
            val c = state("c")
            a.transitionConditionally<ProbeEvent>("t1") { direction = { firstHit = true; targetState(b) } }
            a.transitionConditionally<ProbeEvent>("t2") { direction = { secondHit = true; targetState(c) } }
        }
        machine.startBlocking()

        machine.processEventBlocking(ProbeEvent)
        assertTrue(firstHit, "第一条转移应被求值")
        assertTrue(!secondHit, "取到第一条后应短路，不再求值第二条")
    }

    /**
     * `doNotThrowOnMultipleTransitionsMatch = true` 时，事件必须仍能穿透「自身无转移的中间状态」，
     * 命中更深层状态的转移。
     *
     * 这条语义真的被破坏过：`BaseStateImpl.recursiveFindUniqueResolvedTransition` 的快路径一旦写成
     * `transitions.isEmpty() && (states.isEmpty() || doNotThrowOnMultipleTransitionsMatch)`，
     * 有子状态的中间层就会直接返回 null，整棵子树的转移被静默丢弃（事件变成 IGNORED）。
     * 该 flag 下不存在「多转移匹配」异常可以依靠，失效是完全静默的，所以必须由测试锁住。
     */
    @Test
    fun `doNotThrow 模式下中间层无转移时仍能命中深层转移`() {
        var triggered = false
        val machine = createStdLibStateMachine(
            start = false,
            creationArguments = buildCreationArguments {
                doNotThrowOnMultipleTransitionsMatch = true
            }
        ) {
            val top = initialState("top")
            val mid = top.state("mid")
            top.setInitialState(mid)
            val leaf = mid.state("leaf")
            mid.setInitialState(leaf)

            // 转移挂在最深一层：top 与 mid 自身都没有转移
            leaf.transitionConditionally<ProbeEvent>("leafT") {
                direction = {
                    triggered = true
                    targetState(leaf)
                }
            }
        }
        machine.startBlocking()

        val result = machine.processEventBlocking(ProbeEvent)
        assertEquals(ProcessingResult.PROCESSED, result, "深层转移必须被解析，而不是静默 IGNORED")
        assertTrue(triggered, "深层转移的 direction 应被求值")
    }

    // ══════════════════════════════════════════════════════════
    // 无转移的状态（快路径）
    // ══════════════════════════════════════════════════════════

    @Test
    fun `无转移的状态不解析出任何转移`() {
        val machine = createStdLibStateMachine(start = false) {
            initialState("only")
        }
        machine.startBlocking()

        var completions = 0
        machine.onTransitionComplete { _, _ -> completions++ }

        val result = machine.processEventBlocking(ProbeEvent)
        assertEquals(ProcessingResult.IGNORED, result)
        assertEquals(0, completions, "被忽略的事件不应触发 onTransitionComplete")
    }

    /** 深嵌套链上，中间层没有转移时事件仍能冒泡到最上层并命中 */
    @Test
    fun `中间层无转移时事件冒泡到上层转移`() {
        var topTriggered = false
        val machine = createStdLibStateMachine(start = false) {
            val top = initialState("top")
            val mid = top.state("mid")
            top.setInitialState(mid)
            val leaf = mid.state("leaf")
            mid.setInitialState(leaf)

            top.transitionConditionally<ProbeEvent>("topT") {
                direction = { topTriggered = true; targetState(top) }
            }
        }
        machine.startBlocking()

        machine.processEventBlocking(ProbeEvent)
        assertTrue(topTriggered, "叶子/中间层无转移，事件应冒泡到 top 并命中")
    }

    // ══════════════════════════════════════════════════════════
    // 监听器快路径
    // ══════════════════════════════════════════════════════════

    /**
     * 优化跳过了「无监听器时的 activeStates()」。有监听器时必须仍然收到正确内容。
     *
     * 注意监听器要在 `startBlocking()` **之后**注册：启动本身也是一次转移（StartEvent），
     * 否则会把启动转移也算进去。
     */
    @Test
    fun `有 onComplete 监听器时仍收到正确活跃状态`() {
        val seen = mutableListOf<Set<IState>>()
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<ProbeEvent>("go") { direction = { targetState(b) } }
        }
        machine.startBlocking()

        machine.onTransitionComplete { activeStates, _ -> seen += activeStates }
        machine.processEventBlocking(ProbeEvent)

        assertEquals(1, seen.size, "应恰好收到一次 onTransitionComplete（启动转移不计入）")
        val names = seen.single().mapNotNull { it.name }.toSet()
        assertTrue("b" in names, "活跃状态应包含目标状态 b，实际：$names")
    }

    /**
     * 关键回归点：优化后当 `_machineListeners` 与 `transition.listeners` **同时为空**时，
     * `activeStates()` 全树遍历被整体跳过。一旦加入监听器，这条路径必须重新生效。
     */
    @Test
    fun `无监听器与有监听器的 onComplete 行为对照`() {
        val probe = object : Transition.Listener {
            var calls = 0
            var lastActive: Set<IState> = emptySet()
            override suspend fun onComplete(activeStates: Set<IState>, transitionParams: TransitionParams<*>) {
                calls++
                lastActive = activeStates
            }
        }

        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            val t = a.transitionConditionally<ProbeEvent>("go") { direction = { targetState(b) } }
            t.addListener(probe)
        }
        machine.startBlocking()

        // 转移级监听器存在 → 快路径必须让位，onComplete 必须收到真实活跃状态
        machine.processEventBlocking(ProbeEvent)
        assertEquals(1, probe.calls, "存在转移级监听器时必须调用 onComplete")
        assertTrue(
            probe.lastActive.any { it.name == "b" },
            "onComplete 必须收到真实计算的活跃状态（含目标状态 b），实际：${probe.lastActive.map { it.name }}"
        )
    }

    /** 转移自身的 onComplete 监听器同样必须被调用 */
    @Test
    fun `转移级 onComplete 监听器仍被调用`() {
        var calls = 0
        lateinit var activeSeen: Set<IState>
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            val t = a.transitionConditionally<ProbeEvent>("go") { direction = { targetState(b) } }
            t.addListener(object : Transition.Listener {
                override suspend fun onComplete(activeStates: Set<IState>, transitionParams: TransitionParams<*>) {
                    calls++
                    activeSeen = activeStates
                }
            })
        }
        machine.startBlocking()

        val callsAfterStart = calls
        machine.processEventBlocking(ProbeEvent)
        assertEquals(callsAfterStart + 1, calls, "转移级 onComplete 应被调用一次")
        assertTrue(activeSeen.isNotEmpty(), "onComplete 应收到非空活跃状态集合")
    }

    /** 无任何监听器时，转移语义必须完全不变 */
    @Test
    fun `无监听器时转移语义不变`() {
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<ProbeEvent>("go") { direction = { targetState(b) } }
        }
        machine.startBlocking()

        val result = machine.processEventBlocking(ProbeEvent)
        assertEquals(ProcessingResult.PROCESSED, result)
        assertNull(machine.findState("a")?.takeIf { it.isActive }, "a 应已退出")
        assertTrue(machine.findState("b")!!.isActive, "b 应已激活")
    }

    /** 监听器静默段（`openListenersMutationSection`）内不应发出任何通知 */
    @Test
    fun `监听器静默段内转移不通知外部`() {
        var notified = 0
        val machine = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<ProbeEvent>("go") { direction = { targetState(b) } }
        }
        machine.startBlocking()

        // 启动之后注册，避免把 StartEvent 转移计入
        machine.onTransitionComplete { _, _ -> notified++ }

        val section = (machine as InternalStateMachine).openListenersMutationSection()
        machine.processEventBlocking(ProbeEvent)
        section.close()

        assertEquals(0, notified, "监听器静默段内不应发出通知")

        // 关闭静默段后恢复通知
        machine.processEventBlocking(ProbeEvent)
        assertEquals(0, notified, "此时 b 状态已无 go 转移，仍不应通知")
    }

    // ══════════════════════════════════════════════════════════
    // StateGraphController 集成层（真实使用方式）
    // ══════════════════════════════════════════════════════════

    /** 优化后 StateGraphController 的转移行为必须与优化前一致 */
    @Test
    fun `StateGraphController 事件转移行为不变`() {
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "A",
                transitions = listOf(StateTransition(event = "go", target = "B", condition = StateCondition.True))
            ),
            nodes = listOf(StateNode("B", transitions = emptyList()))
        )
        val controller = StateGraphController(graph)
        controller.start()
        assertEquals("A", controller.currentNode.name)

        val event = controller.triggerEvent("go")
        assertEquals("B", controller.currentNode.name)
        assertEquals("B", event.targetNode?.name, "targetNode 应指向目标节点")
    }

    /** auto 转移（event == null）路径必须仍然每帧求值 */
    @Test
    fun `StateGraphController auto 转移仍然生效`() {
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "A",
                transitions = listOf(StateTransition(event = null, target = "B", condition = StateCondition.True))
            ),
            nodes = listOf(StateNode("B", transitions = emptyList()))
        )
        val controller = StateGraphController(graph)
        controller.start()
        controller.progress(0.05f)
        assertEquals("B", controller.currentNode.name)
    }

    /** 自转移（target == source）在优化后不应变成「无转移」 */
    @Test
    fun `StateGraphController 自转移仍然触发 onTriggered`() {
        var triggers = 0
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "self",
                transitions = listOf(StateTransition(event = "refresh", target = "self", condition = StateCondition.True))
            ),
            nodes = emptyList()
        )
        val controller = object : StateGraphController(graph) {
            override fun onTriggered(
                event: StateGraphController.ActionEvent,
                source: StateNode?,
                target: StateNode?
            ) {
                triggers++
            }
        }
        controller.start()

        controller.triggerEvent("refresh")
        controller.triggerEvent("refresh")
        assertEquals(2, triggers, "自转移每次都应触发 onTriggered")
    }

    private object ProbeEvent : Event
    private object OtherEvent : Event
}
