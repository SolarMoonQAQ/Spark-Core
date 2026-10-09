package cn.solarmoon.spark_core.state_machine.perf

import cn.solarmoon.spark_core.animation.anim.AnimState
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.graph.StateTransition
import org.junit.jupiter.api.Test
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.statemachine.stopBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.targetState
import kotlin.random.Random

/**
 * KStateMachine 性能基准。
 *
 * 目标：把「用起来体感卡」拆成可测量、可回归的数字。
 *
 * 三类测量：
 * 1. [StateGraphController] 每帧 `progress()` 的空转成本 —— 这是 Spark-Core 真正的热路径，
 *    每个实体每个控制器每 tick 都会走一遍。
 * 2. 状态数 N 的缩放 —— 判断每帧成本是 O(1) 还是 O(N)。
 * 3. 底层 `processEventBlocking` 的常数成本 —— 定位「桥接层」的固定开销。
 *
 * 运行：`gradlew test --tests "*KStateMachinePerfTest*"`
 */
class KStateMachinePerfTest {

    init {
        if (!PerfHarness.allocationMeasurementAvailable) {
            println("[警告] 当前 JVM 不支持线程分配量统计，B/op 将显示为 0")
        }
    }

    // ══════════════════════════════════════════════════════════
    // 图构造辅助
    // ══════════════════════════════════════════════════════════

    /**
     * 构造一个 N 节点的「链式」图（贴近真实动画状态机：idle → walk → run → ... ）。
     *
     * - 每个节点恰好 1 条转移：偶数 index 为 auto 转移（event == null），奇数 index 为事件转移。
     * - 条件恒为 false，保证测量的是「求值成本」而不是「转移成本」。
     */
    private fun chainGraph(nodeCount: Int): StateMachineGraph {
        require(nodeCount >= 1)
        val nodes = (0 until nodeCount).map { i ->
            val isLast = i == nodeCount - 1
            val transitions = if (isLast) emptyList() else listOf(
                StateTransition(
                    event = if (i % 2 == 0) null else "evt_$i",
                    target = "n${i + 1}",
                    condition = StateCondition.False
                )
            )
            StateNode(name = "n$i", transitions = transitions)
        }
        return StateMachineGraph(initialNode = nodes.first(), nodes = nodes.drop(1))
    }

    /** 每条转移条件恒真 —— 用于测量「真的发生转移」时的成本 */
    private fun alwaysTransitionGraph(): StateMachineGraph {
        // n0 --auto(true)--> n1 --auto(true)--> n0  (2 节点循环，持续转移)
        val n0 = StateNode(
            name = "n0",
            transitions = listOf(StateTransition(event = null, target = "n1", condition = StateCondition.True))
        )
        val n1 = StateNode(
            name = "n1",
            transitions = listOf(StateTransition(event = null, target = "n0", condition = StateCondition.True))
        )
        return StateMachineGraph(initialNode = n0, nodes = listOf(n1))
    }

    // ══════════════════════════════════════════════════════════
    // 1. StateGraphController 单帧成本 vs 状态数
    // ══════════════════════════════════════════════════════════

    @Test
    fun `每帧 progress 成本随状态数缩放`() {
        PerfHarness.printHeader("1. StateGraphController.progress() 单次调用成本 vs 节点数（无转移发生）")

        val nodeCounts = listOf(1, 5, 10, 20, 40, 80)
        val results = nodeCounts.map { n ->
            val controller = StateGraphController(chainGraph(n))
            controller.start()
            PerfHarness.measureMedian("progress()  N=$n 节点") {
                controller.progress(0.05f)
            }
        }

        println(PerfHarness.report(results))

        val base = results.first().nsPerOp
        println()
        println("  相对 N=1 的倍数（判断是否 O(N)）：")
        results.forEach { r ->
            println("    %-24s  x%.1f".format(r.label.substringAfter("N="), r.nsPerOp / base))
        }
    }

    // ══════════════════════════════════════════════════════════
    // 2. 转移发生时的成本
    // ══════════════════════════════════════════════════════════

    @Test
    fun `发生转移的一帧成本`() {
        PerfHarness.printHeader("2. 每帧 progress() 成本：不发生转移 vs 持续转移")

        val idle = StateGraphController(chainGraph(20))
        idle.start()
        val idleResult = PerfHarness.measureMedian("progress()  20 节点，条件恒 false（无转移）") {
            idle.progress(0.05f)
        }

        val busy = StateGraphController(alwaysTransitionGraph())
        busy.start()
        val busyResult = PerfHarness.measureMedian("progress()  2 节点，条件恒 true（每帧转移）") {
            busy.progress(0.05f)
        }

        println(PerfHarness.report(listOf(idleResult, busyResult)))
        println()
        println("  → 一次「真实转移」的边际成本 ≈ %.0f ns，%.0f B"
            .format(busyResult.nsPerOp - idleResult.nsPerOp, busyResult.bytesPerOp - idleResult.bytesPerOp))
    }

    // ══════════════════════════════════════════════════════════
    // 3. 事件驱动路径（event != null，O(1) Map 查找）
    // ══════════════════════════════════════════════════════════

    @Test
    fun `事件触发成本随状态数缩放`() {
        PerfHarness.printHeader("3. triggerEvent() 成本 vs 节点数（事件在 Map 中不存在 → 空转移）")

        val nodeCounts = listOf(1, 10, 40, 80)
        val results = nodeCounts.map { n ->
            val controller = StateGraphController(chainGraph(n))
            controller.start()
            PerfHarness.measureMedian("triggerEvent(\"miss\")  N=$n") {
                controller.triggerEvent("miss")
            }
        }
        println(PerfHarness.report(results))
    }

    // ══════════════════════════════════════════════════════════
    // 4. 底层桥接常数成本（无任何转移的空状态机）
    // ══════════════════════════════════════════════════════════

    @Test
    fun `底层 processEventBlocking 常数成本`() {
        PerfHarness.printHeader("4. 底层 KStateMachine 常数成本（空机器 = 纯桥接/协程层开销）")

        val machine = createStdLibStateMachine(start = false) {
            initialState("only")
        }
        machine.startBlocking()

        object : ru.nsk.kstatemachine.event.Event {}.let { evt ->
            val r = PerfHarness.measureMedian("processEventBlocking(未匹配事件)") {
                machine.processEventBlocking(evt)
            }
            println(PerfHarness.report(listOf(r)))
        }

        val manyStates = createStdLibStateMachine(start = false) {
            initialState("s0")
            (1 until 40).forEach { state("s$it") }
        }
        manyStates.startBlocking()
        val r2 = PerfHarness.measureMedian("processEventBlocking  40 个兄弟状态，无转移") {
            manyStates.processEventBlocking(object : ru.nsk.kstatemachine.event.Event {})
        }
        println(PerfHarness.report(listOf(r2)))
        println()
        println("  → 第 4 项与第 1 项对比可判断：成本主要来自 kstatemachine 内部，还是 StateGraphController 包装层")
    }

    // ══════════════════════════════════════════════════════════
    // 5. activeStates() 成本（AnimInstance.state 每次读取都调用它）
    // ══════════════════════════════════════════════════════════

    @Test
    fun `activeStates 查询成本`() {
        PerfHarness.printHeader("5. activeStates() 查询成本（AnimInstance.isFinished / isInTransition 每次都调用）")

        val results = listOf(1, 4, 16, 64).map { n ->
            val machine = createStdLibStateMachine(start = false) {
                initialState("s0")
                (1 until n).forEach { state("s$it") }
            }
            machine.startBlocking()
            PerfHarness.measureMedian("activeStates()  N=$n 兄弟状态") {
                machine.activeStates()
            }
        }
        println(PerfHarness.report(results))
        println()
        println("  注意：AnimInstance.state 的实现是")
        println("    AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: \"IDLE\")")
        println("  即每次读取都要：遍历 + 建 Set + 拼字符串 + valueOf 反射查表。")
    }

    // ══════════════════════════════════════════════════════════
    // 6. 全服规模推算
    // ══════════════════════════════════════════════════════════

    @Test
    fun `全服规模推算`() {
        PerfHarness.printHeader("6. 全服规模推算：每 tick 全部控制器 progress 一次")

        val controllerCounts = listOf(10, 50, 200)
        val nodesPerController = 20

        val controllers = (1..controllerCounts.max()).map {
            StateGraphController(chainGraph(nodesPerController)).also { c -> c.start() }
        }

        controllerCounts.forEach { count ->
            val slice = controllers.subList(0, count)
            val r = PerfHarness.measureMedian("$count 个控制器 × $nodesPerController 节点 / tick", rounds = 3) {
                for (c in slice) c.progress(0.05f)
            }
            val perTickMs = r.nsPerOp / 1_000_000.0
            println("%-52s %8.3f ms/tick    %7.2f%% of 50ms(20TPS budget)   %8.0f KB/tick"
                .format(r.label, perTickMs, perTickMs / 50.0 * 100, r.bytesPerOp / 1024))
        }
        println()
        println("  参考：Minecraft 服务端 tick 预算 50ms。以上仅为状态机开销，不含渲染/物理/AI。")
    }

    // ══════════════════════════════════════════════════════════
    // 7. 分配热点定位
    // ══════════════════════════════════════════════════════════

    @Test
    fun `分配热点定位`() {
        PerfHarness.printHeader("7. 分配解剖：把每帧成本拆到各阶段")

        println("  (a) 纯 Map.get 基线（用于判断测量夹具本身的开销）")
        val map = HashMap<String, Int>()
        val baseResults = PerfHarness.measureMedian("HashMap.get 基线") { map["x"] }
        println(PerfHarness.report(listOf(baseResults)))

        println()
        println("  (b) 空状态机 startBlocking/stopBlocking（每次调用都会新建协程 Continuation）")
        val r = PerfHarness.measureMedian("start+stop 一轮（空机器）") {
            val m = createStdLibStateMachine(start = false) { initialState("a") }
            m.startBlocking()
            m.stopBlocking()
        }
        println(PerfHarness.report(listOf(r)))

        println()
        println("  (c) 单次 processEventBlocking 的内部结构拆解（N=1 状态，无转移）")
        val machine = createStdLibStateMachine(start = false) { initialState("a") }
        machine.startBlocking()
        val evt = object : ru.nsk.kstatemachine.event.Event {}
        val r2 = PerfHarness.measureMedian("processEventBlocking  N=1") {
            machine.processEventBlocking(evt)
        }
        println(PerfHarness.report(listOf(r2)))
        println()
        println("  拆解（源码对照）：")
        println("    1. coroutineAbstraction.runBlocking { ... }  → 分配 Continuation + 协程状态机")
        println("    2. EventAndArgument 分配                       → 每事件 1 个对象")
        println("    3. getCurrentStates() → listOfNotNull          → 每层 1 个 List")
        println("    4. .filter{}.mapNotNull{}.ifEmpty{}            → 每层 3 个中间 List")
        println("    5. findTransitionsByEvent: transitions.filter{} → 每层 1 个 List")
        println("    6. .map{}.filter{}                             → 每层 2 个中间 List + N 个 Pair")
        println("    7. listeners.toList()（stateNotify/machineNotify/transitionNotify）→ 每次通知 1 个 List")
        println("    8. recursiveAfterTransitionComplete 遍历整棵子树")
        println("    9. activeStates()（仅在转移时）→ 建 Set + 遍历")
    }

    // ══════════════════════════════════════════════════════════
    // 8. 深树 / 嵌套结构（findDepth 无缓存的代价）
    // ══════════════════════════════════════════════════════════

    @Test
    fun `深嵌套树的转移成本`() {
        PerfHarness.printHeader("8. 嵌套深度对转移成本的影响（findDepth() 每次都走到根，无缓存）")

        fun deepMachine(depth: Int): ru.nsk.kstatemachine.statemachine.StateMachine {
            return createStdLibStateMachine(start = false) {
                var parent: IState = initialState("d0")
                val all = mutableListOf<IState>(parent)
                for (i in 1 until depth) {
                    val child = parent.state("d$i")
                    parent.setInitialState(child)
                    parent = child
                    all += child
                }
                // 最深层加一条自转移，事件恒匹配 → 每次事件都真的走一次完整转移路径
                val deepest = all.last()
                deepest.transitionConditionally<ru.nsk.kstatemachine.event.Event>("t") {
                    direction = { targetState(deepest) }
                }
            }
        }

        val results = listOf(2, 4, 8, 16).map { d ->
            val m = deepMachine(d)
            m.startBlocking()
            val evt = object : ru.nsk.kstatemachine.event.Event {}
            PerfHarness.measureMedian("depth=$d  自转移") {
                m.processEventBlocking(evt)
            }
        }
        println(PerfHarness.report(results))
    }

    // ══════════════════════════════════════════════════════════
    // 9. 噪声控制
    // ══════════════════════════════════════════════════════════

    /**
     * 对照实验：只做随机数生成，不做任何状态机操作。
     * 用于确认 1~8 项测到的成本确实来自状态机，而不是测量夹具本身。
     */
    @Test
    fun `测量夹具自噪声对照`() {
        PerfHarness.printHeader("9. 对照：只测量夹具本身（无状态机操作）")
        val rnd = Random(42)
        var sink = 0
        val r = PerfHarness.measureMedian("Random.nextInt() 单次") { sink += rnd.nextInt() }
        println(PerfHarness.report(listOf(r)))
        check(sink != Int.MIN_VALUE) // 防止 JIT 消除整个循环
    }

    // ══════════════════════════════════════════════════════════
    // 10. 扇出：单个状态挂大量转移（真正的 O(N) 扫描路径）
    // ══════════════════════════════════════════════════════════

    /**
     * 构造「中枢」图：`hub` 一个状态上挂 [fanOut] 条 conditional 转移，全部条件恒 false。
     *
     * 这是唯一能让 `findTransitionsByEvent` 真正线性扫描的场景：
     * `recursiveFindUniqueResolvedTransition` 只检查**当前活跃状态**，
     * 所以状态总数不重要，**单个状态上的转移条数**才重要。
     */
    private fun hubGraph(fanOut: Int, conditionTrue: Boolean): StateMachineGraph {
        val cond = if (conditionTrue) StateCondition.True else StateCondition.False
        val hubTransitions = (0 until fanOut).map { i ->
            StateTransition(event = null, target = "leaf$i", condition = cond)
        }
        val hub = StateNode(name = "hub", transitions = hubTransitions)
        val leaves = (0 until fanOut).map { i -> StateNode(name = "leaf$i", transitions = emptyList()) }
        return StateMachineGraph(initialNode = hub, nodes = leaves)
    }

    @Test
    fun `单状态转移条数对每帧成本的影响`() {
        PerfHarness.printHeader("10. 扇出：progress() 单帧成本 vs 「当前状态」上的转移条数（条件全部不成立）")

        val fanOuts = listOf(1, 4, 16, 64)
        val scan = fanOuts.map { n ->
            val c = StateGraphController(hubGraph(n, conditionTrue = false))
            c.start()
            PerfHarness.measureMedian("progress()  hub 上 $n 条转移（全 false）") { c.progress(0.05f) }
        }
        println("  (a) 扫描成本：条件全部不成立，只走匹配 + 求值")
        println(PerfHarness.report(scan))

        val base = scan.first().nsPerOp
        println()
        println("  相对 1 条转移的倍数：")
        scan.forEach { println("    %-34s x%.1f".format(it.label.substringAfter("hub 上 "), it.nsPerOp / base)) }

        val hit = fanOuts.map { n ->
            val c = StateGraphController(hubGraph(n, conditionTrue = true))
            c.start()
            PerfHarness.measureMedian("progress()  hub 上 $n 条转移（第 1 条命中→转移）") { c.progress(0.05f) }
        }
        println()
        println("  (b) 命中成本：第 1 条条件成立，立即转移（后续条目不再求值）")
        println(PerfHarness.report(hit))
    }

    // ══════════════════════════════════════════════════════════
    // 11. 嵌套层级：recursiveFindUniqueResolvedTransition 的祖先链行走
    // ══════════════════════════════════════════════════════════

    /**
     * 构造深度为 [depth] 的嵌套控制器链。
     *
     * 每层的图都自带一条条件恒 false 的 auto 转移 —— 真实动画图就是这样：
     * 每一层控制器每帧都要跑一次 `progress()`，而 `progress()` 会：
     *   1. 递归驱动所有活跃子控制器
     *   2. 触发自身 event=null 转移 → 一次完整的 processEventBlocking
     */
    private fun nestedControllers(depth: Int): StateGraphController {
        fun graph(level: Int): StateMachineGraph = StateMachineGraph(
            initialNode = StateNode(
                name = "L$level",
                transitions = listOf(
                    StateTransition(event = null, target = "idle_$level", condition = StateCondition.False)
                ),
                subGraphs = if (level < depth - 1) mapOf("child" to graph(level + 1)) else emptyMap()
            ),
            nodes = listOf(StateNode("idle_$level", transitions = emptyList()))
        )

        fun build(level: Int): StateGraphController {
            val child = if (level < depth - 1) build(level + 1) else null
            return StateGraphController(
                graph(level),
                children = if (child != null) mapOf("child" to child) else emptyMap()
            )
        }

        return build(0)
    }

    @Test
    fun `嵌套层级对每帧成本的影响`() {
        PerfHarness.printHeader("11. 嵌套层级：progress() 单帧成本 vs 控制器层深")

        val depths = listOf(1, 2, 4, 8)
        val results = depths.map { d ->
            val top = nestedControllers(d)
            top.start()
            PerfHarness.measureMedian("progress(dt)  嵌套 $d 层控制器") { top.progress(0.05f) }
        }
        println(PerfHarness.report(results))
        println()
        println("  → progress() 先递归驱动所有活跃子控制器，再触发自身 event=null 转移；")
        println("    控制器链有多深，每帧就要跑多少次完整的事件处理流程。")
    }

    // ══════════════════════════════════════════════════════════
    // 12. AnimInstance.state 的真实开销
    // ══════════════════════════════════════════════════════════

    @Test
    fun `AnimInstance state 属性开销`() {
        PerfHarness.printHeader("12. AnimInstance.state 属性链开销（热路径：isFinished / isInTransition）")

        val machine = createStdLibStateMachine(start = false) {
            initialState("idle")
            state("enter"); state("work"); state("exit")
        }
        machine.startBlocking()

        val r1 = PerfHarness.measureMedian("activeStates() 原始调用") { machine.activeStates() }
        println(PerfHarness.report(listOf(r1)))

        var sink: String? = null
        val r2 = PerfHarness.measureMedian("activeStates().firstOrNull()?.name?.uppercase()") {
            sink = machine.activeStates().firstOrNull()?.name?.uppercase()
        }
        println(PerfHarness.report(listOf(r2)))

        val r3 = PerfHarness.measureMedian("完整 AnimInstance.state 表达式（含 valueOf）") {
            sink = AnimState.valueOf(
                machine.activeStates().firstOrNull()?.name?.uppercase() ?: "IDLE"
            ).name
        }
        println(PerfHarness.report(listOf(r3)))
        check(sink != null)

        println()
        println("  AnimInstance 源码：")
        println("    val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: \"IDLE\")")
        println("    val isFinished get() = state == AnimState.IDLE")
        println("    val isInTransition get() = state in listOf(AnimState.ENTER, AnimState.EXIT)   ← 每次还新建一个 List")
    }

    // ══════════════════════════════════════════════════════════
    // 13. 单次真实转移的拆解
    // ══════════════════════════════════════════════════════════

    @Test
    fun `单次真实转移成本拆解`() {
        PerfHarness.printHeader("13. 单次真实转移的边际成本（对照：无转移的一帧）")

        // 基线：一帧什么都不发生
        val idle = StateGraphController(hubGraph(1, conditionTrue = false))
        idle.start()
        val idleR = PerfHarness.measureMedian("progress()  无转移发生") { idle.progress(0.05f) }
        println(PerfHarness.report(listOf(idleR)))

        // 转移：hub --auto(true)--> leaf0 --auto(true)--> hub  (来回弹)
        val n0 = StateNode("n0", transitions = listOf(
            StateTransition(event = null, target = "n1", condition = StateCondition.True)))
        val n1 = StateNode("n1", transitions = listOf(
            StateTransition(event = null, target = "n0", condition = StateCondition.True)))
        val busy = StateGraphController(StateMachineGraph(initialNode = n0, nodes = listOf(n1)))
        busy.start()
        val busyR = PerfHarness.measureMedian("progress()  每帧都发生转移") { busy.progress(0.05f) }
        println(PerfHarness.report(listOf(busyR)))

        println()
        println("  → 一次转移的边际成本 ≈ %.0f ns / %.0f B".format(
            busyR.nsPerOp - idleR.nsPerOp, busyR.bytesPerOp - idleR.bytesPerOp))

        // 事件驱动（走 Map 查找）
        val evtGraph = StateMachineGraph(
            initialNode = StateNode("e0", transitions = listOf(
                StateTransition(event = "go", target = "e1", condition = StateCondition.True))),
            nodes = listOf(
                StateNode("e1", transitions = listOf(
                    StateTransition(event = "go", target = "e0", condition = StateCondition.True))))
        )
        val evtCtl = StateGraphController(evtGraph)
        evtCtl.start()
        val evtR = PerfHarness.measureMedian("triggerEvent(\"go\")  每帧都发生转移") { evtCtl.triggerEvent("go") }
        println(PerfHarness.report(listOf(evtR)))
    }

    // ══════════════════════════════════════════════════════════
    // 14. 转移时对整棵子树的遍历（recursiveAfterTransitionComplete）
    // ══════════════════════════════════════════════════════════

    /**
     * 宽树：`hub` 是根状态，带 [width] 个兄弟子状态；每帧都真的发生一次 hub→hub 自转移。
     *
     * 转移完成后 `doProcessEvent` 会执行：
     *   - `recursiveAfterTransitionComplete` → 遍历**整棵子树**（O(宽度)）；2026-10-10 起该遍历按子树剪枝，
     *     本生态没有覆写者，因此这一项不再随宽度增长（剪枝前 176 B / 子状态）
     *   - `activeStates()`                     → `accept(GetActiveStatesVisitor)`（只走活跃路径，固定 ~272 B）
     *   - `onComplete` / `onTransitionComplete` 通知
     */
    private fun wideTreeGraph(width: Int): StateMachineGraph {
        val hub = StateNode(
            name = "hub",
            transitions = listOf(
                StateTransition(event = null, target = "hub", condition = StateCondition.True)
            )
        )
        val siblings = (0 until width).map { StateNode("sib$it", emptyList()) }
        return StateMachineGraph(initialNode = hub, nodes = siblings)
    }

    @Test
    fun `转移时子树宽度的影响`() {
        PerfHarness.printHeader("14. 每次转移的固定成本 vs 兄弟状态数（转移后要遍历整棵子树）")

        val widths = listOf(1, 10, 50, 150)
        val results = widths.map { w ->
            val c = StateGraphController(wideTreeGraph(w))
            c.start()
            PerfHarness.measureMedian("progress()  每帧转移  兄弟状态数=$w") { c.progress(0.05f) }
        }
        println(PerfHarness.report(results))

        val base = results.first()
        println()
        println("  相对 1 个兄弟状态的增量（每次转移的固定成本）：")
        results.drop(1).forEach {
            println("    %-28s +%.0f ns  +%.0f B"
                .format(it.label.substringAfter("兄弟状态数="), it.nsPerOp - base.nsPerOp, it.bytesPerOp - base.bytesPerOp))
        }
        println()
        println("  剪枝前成本随兄弟数增长，来源是 `recursiveAfterTransitionComplete` 的整树遍历；")
        println("  该遍历现已按子树剪枝，`activeStates()` 又只走活跃路径 → 增量只剩桥接层本身。")
    }

    // ══════════════════════════════════════════════════════════
    // 15. 直接量化协程桥接层的固定开销
    // ══════════════════════════════════════════════════════════

    @Test
    fun `协程桥接层固定开销`() {
        PerfHarness.printHeader("15. StdLibCoroutineAbstraction.runBlocking 的固定开销")

        // 直接量化 runBlocking 本身：用它包一个几乎没有内容的 suspend 块
        val abstraction = object : ru.nsk.kstatemachine.coroutines.CoroutineAbstraction {
            private val inner = createStdLibStateMachine(start = false) { initialState("x") }.coroutineAbstraction
            override fun <R : Any> runBlocking(block: suspend () -> R): R = inner.runBlocking(block)
            override suspend fun <R : Any> withContext(block: suspend () -> R): R = inner.withContext(block)
        }

        var sink = 0
        val r1 = PerfHarness.measureMedian("runBlocking { sink++ } 空块") {
            abstraction.runBlocking { sink++ }
        }
        println(PerfHarness.report(listOf(r1)))

        val machine = createStdLibStateMachine(start = false) { initialState("only") }
        machine.startBlocking()
        val evt = object : ru.nsk.kstatemachine.event.Event {}
        val r2 = PerfHarness.measureMedian("完整 processEventBlocking（1 状态，无转移）") {
            machine.processEventBlocking(evt)
        }
        println(PerfHarness.report(listOf(r2)))

        println()
        println("  → 二者之差 ≈ 事件查找 + 通知 + 分配的全部剩余部分")
        println("    runBlocking 占单次事件成本的 %.0f%%".format(r1.nsPerOp / r2.nsPerOp * 100))
        check(sink != Int.MIN_VALUE)
    }
}
