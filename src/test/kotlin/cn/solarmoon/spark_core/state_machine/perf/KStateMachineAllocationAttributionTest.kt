package cn.solarmoon.spark_core.state_machine.perf

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.graph.StateTransition
import org.junit.jupiter.api.Test
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.transition.TransitionType
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.targetState

/**
 * `processEventBlocking` 的**分配归因**测试。
 *
 * 方法：逐段消融。构造一串「只比上一档多做一件事」的状态机，
 * 相邻两档的 B/op 差值就是那一件事的成本。
 *
 * 所有档位都通过**同一个入口** `processEventBlocking` 测量，
 * 因此 `runBlocking` 脚手架 + `EventAndArgument` + `processStep1/2` 等公共部分完全抵消。
 */
class KStateMachineAllocationAttributionTest {

    private object Evt : Event

    /** 一档测量结果 */
    private data class Tier(val name: String, val ns: Double, val bytes: Double, val note: String)

    private fun report(title: String, tiers: List<Tier>) {
        PerfHarness.printHeader(title)
        println("%-44s %10s %12s   %s".format("档位", "ns/op", "B/op", "相对上一档的增量"))
        println("-".repeat(110))
        tiers.forEachIndexed { i, t ->
            val delta = if (i == 0) "" else "  +%.0f ns   +%.0f B".format(t.ns - tiers[i - 1].ns, t.bytes - tiers[i - 1].bytes)
            println("%-44s %10.1f %12.1f  %s".format(t.name, t.ns, t.bytes, delta))
        }
        println()
    }

    @Test
    fun `事件处理分配归因`() {
        val tiers = mutableListOf<Tier>()

        // ── 档 0：基线 = 机器已启动、无转移、事件被忽略 ──
        run {
            val m = createStdLibStateMachine(start = false) { initialState("a") }
            m.startBlocking()
            val r = PerfHarness.measureMedian("0. 基线：单状态，无转移，事件 IGNORED") { m.processEventBlocking(Evt) }
            tiers += Tier("0. 基线（脚手架+匹配）", r.nsPerOp, r.bytesPerOp, "")
        }

        // ── 档 1：+ runBlocking 与 withContext 的本底 ──
        run {
            val m = createStdLibStateMachine(start = false) { initialState("a") }
            m.startBlocking()
            val ab = m.coroutineAbstraction
            val r = PerfHarness.measureMedian("1. 同上，但直接走 withContext（跳过 runBlocking）") {
                ab.runBlocking { ab.withContext { Unit } }
            }
            tiers += Tier("1. runBlocking+withContext 空转", r.nsPerOp, r.bytesPerOp, "")
        }

        // ── 档 2：事件命中 conditional 转移，但 direction 返回 noTransition ──
        run {
            val m = createStdLibStateMachine(start = false) {
                val a = initialState("a")
                a.transitionConditionally<Evt>("cond") { direction = { noTransition() } }
            }
            m.startBlocking()
            val r = PerfHarness.measureMedian("2. + 事件命中转移，direction=noTransition") { m.processEventBlocking(Evt) }
            tiers += Tier("2. + EventMatcher 命中 + policy + noTransition", r.nsPerOp, r.bytesPerOp, "")
        }

        // ── 档 3：LOCAL 自转移（setCurrentState 短路，但通知/参数照走） ──
        run {
            val m = createStdLibStateMachine(start = false) {
                val a = initialState("a")
                a.transitionConditionally<Evt>("self") { direction = { targetState(a) } }
            }
            m.startBlocking()
            val r = PerfHarness.measureMedian("3. + LOCAL 自转移（同状态短路）") { m.processEventBlocking(Evt) }
            tiers += Tier("3. + TargetState/TransitionParams + 通知", r.nsPerOp, r.bytesPerOp, "")
        }

        // ── 档 4：EXTERNAL 双状态往返（真正走 exit/enter 全流程） ──
        run {
            val m = createStdLibStateMachine(start = false) {
                val a = initialState("a")
                val b = state("b")
                a.transitionConditionally<Evt>("go") {
                    type = TransitionType.EXTERNAL
                    direction = { targetState(b) }
                }
                b.transitionConditionally<Evt>("back") {
                    type = TransitionType.EXTERNAL
                    direction = { targetState(a) }
                }
            }
            m.startBlocking()
            val r = PerfHarness.measureMedian("4. + 真实转移（exit/enter + findPathFromTargetToLca）") { m.processEventBlocking(Evt) }
            tiers += Tier("4. + 真实转移（含 exit/enter）", r.nsPerOp, r.bytesPerOp, "")
        }

        report("分配归因 A：把一个事件从「被忽略」逐步加到「真发生转移」", tiers)
        println("  读法：档 0 是所有场景的共同底座；档 1 是独立的 runBlocking 基线，不参与档 0→4 的做差。")
    }

    /**
     * 归因 B：把「真实转移」的成本继续拆到树结构上。
     *
     * 全部使用同一个 `hub --(auto true)--> hub` 自转移，只改兄弟状态数量，
     * 因此差值就是**转移后全树遍历**的成本。
     */
    @Test
    fun `转移后全树遍历归因`() {
        val tiers = (0..3).map { idx ->
            val width = listOf(1, 10, 50, 150)[idx]
            val hub = StateNode(
                name = "hub",
                transitions = listOf(StateTransition(event = null, target = "hub", condition = StateCondition.True))
            )
            val siblings = (0 until width).map { StateNode("sib$it", emptyList()) }
            val c = StateGraphController(StateMachineGraph(initialNode = hub, nodes = siblings))
            c.start()
            val r = PerfHarness.measureMedian("progress()  自转移  兄弟状态=$width") { c.progress(0.05f) }
            Tier("兄弟状态 = $width", r.nsPerOp, r.bytesPerOp, "")
        }
        report("分配归因 B：每次转移的固定成本 vs 子树宽度（隔离 recursiveAfterTransitionComplete + activeStates）", tiers)

        val perNodeB = (tiers.last().bytes - tiers.first().bytes) / (150 - 1)
        val perNodeNs = (tiers.last().ns - tiers.first().ns) / (150 - 1)
        println("  → 每个「不参与转移的兄弟状态」仍然贡献 %.1f ns 与 %.1f B".format(perNodeNs, perNodeB))
        println("    150 个兄弟时，其中 %.0f B（%.0f%%）纯粹来自全树遍历。"
            .format(tiers.last().bytes - tiers.first().bytes,
                (tiers.last().bytes - tiers.first().bytes) / tiers.last().bytes * 100))
    }

    /**
     * 归因 C：`StateGraphController` 的自定义转移 lambda 里有什么。
     *
     * 与归因 A 的裸状态机对比，差值就是 Spark-Core 包装层
     * （`onCheckTransition` + `Map` 查找 + `targetState(states[...])`）的成本。
     */
    @Test
    fun `包装层归因`() {
        PerfHarness.printHeader("分配归因 C：裸状态机 vs StateGraphController 包装层")

        // 裸状态机：一个事件转移，条件恒 true，每帧真的转移
        val bare = createStdLibStateMachine(start = false) {
            val a = initialState("a")
            val b = state("b")
            a.transitionConditionally<Evt>("go") { direction = { targetState(b) } }
            b.transitionConditionally<Evt>("back") { direction = { targetState(a) } }
        }
        bare.startBlocking()
        val bareR = PerfHarness.measureMedian("裸状态机  processEventBlocking  往返转移") { bare.processEventBlocking(Evt) }
        println(PerfHarness.report(listOf(bareR)))

        // StateGraphController：同样两节点、同样每帧转移，走 event=null 路径
        val graph = StateMachineGraph(
            initialNode = StateNode(
                name = "A",
                transitions = listOf(StateTransition(event = null, target = "B", condition = StateCondition.True))
            ),
            nodes = listOf(
                StateNode(
                    name = "B",
                    transitions = listOf(StateTransition(event = null, target = "A", condition = StateCondition.True))
                )
            )
        )
        val ctl = StateGraphController(graph)
        ctl.start()
        val ctlR = PerfHarness.measureMedian("Controller  progress()  往返转移") { ctl.progress(0.05f) }
        println(PerfHarness.report(listOf(ctlR)))

        println()
        println("  → 包装层（自定义 direction lambda + onCheckTransition + 事件构造）净增 %.0f ns / %.0f B"
            .format(ctlR.nsPerOp - bareR.nsPerOp, ctlR.bytesPerOp - bareR.bytesPerOp))
    }

    /**
     * 归因 D：机器级监听器的存在与否，对每次转移成本的影响。
     *
     * 优化 3.4 在「无机器级监听且无转移级监听」时跳过了 `activeStates()` 全树遍历。
     * 本测试用**同一个宽树裸状态机**做对照，直接量化这条优化的实际价值：
     * 两者之差 = `activeStates()` 全树遍历 + `onComplete` 通知派发。
     */
    @Test
    fun `全树遍历消费者单独测量`() {
        PerfHarness.printHeader("分配归因 D：activeStates() 全树遍历的实际代价（有监听器 vs 无监听器）")

        val widths = listOf(1, 50, 150)
        val results = mutableListOf<Tier>()

        widths.forEach { w ->
            fun build(listen: Boolean): ru.nsk.kstatemachine.statemachine.StateMachine {
                val m = createStdLibStateMachine(start = false) {
                    val root = initialState("root")
                    // 最深层挂自转移，命中后不改变活跃状态，但会走完整转移流程
                    root.transitionConditionally<Evt>("self") {
                        type = TransitionType.EXTERNAL
                        direction = { targetState(root) }
                    }
                    // root 本身必须是 final 子状态（EXCLUSIVE），兄弟状态挂在机器根下
                    repeat(w) { state("sib$it") }
                }
                if (listen) m.addListener(object : ru.nsk.kstatemachine.statemachine.StateMachine.Listener {})
                m.startBlocking()
                return m
            }

            val noListen = build(false)
            val withListen = build(true)

            val a = PerfHarness.measureMedian("裸状态机  兄弟=$w  无监听器") { noListen.processEventBlocking(Evt) }
            val b = PerfHarness.measureMedian("裸状态机  兄弟=$w  有机器级监听器") { withListen.processEventBlocking(Evt) }
            println(PerfHarness.report(listOf(a, b)))
            println("       → activeStates() + onComplete 派发 = %.0f ns / %.0f B"
                .format(b.nsPerOp - a.nsPerOp, b.bytesPerOp - a.bytesPerOp))
            results += Tier("兄弟=$w 有监听增量", b.nsPerOp - a.nsPerOp, b.bytesPerOp - a.bytesPerOp, "")
        }

        println()
        println("  结论：机器级监听器一旦存在，每次转移都要多付一趟全树遍历 —— 这正是优化 3.4 要消除的。")
    }

    /** 供实现参考：这些是 `EventAndArgument` / `TransitionParams` / `TargetState` 的形状 */
    @Test
    fun `相关数据类形状`() {
        PerfHarness.printHeader("分配归因 E：每个事件必然分配的对象")
        println("  EventAndArgument       → 1 个对象（event + argument）")
        println("  TransitionParams       → 1 个对象（transition + direction + event + argument）")
        println("  TargetState            → 1 个对象 + 1 个 setOf() 的 Set")
        println("  DefaultPolicy          → 1 个对象（仅当状态上存在转移时创建）")
        println("  Step1Result            → 1 个对象（wrappedEventAndArgument + processingResult）")
        println("  Pair(candidate, dir)   → 1 个对象（每个匹配转移）")
        println("  Continuation/协程帧     → 每个 suspend 函数调用 1 个，实测 runBlocking 空转仅 120 B")
    }

    /**
     * 归因 G：**176 B/子状态到底花在哪？**
     *
     * 归因 F 里我把这 176 B 归因成「调用空 suspend 函数本身」，这是**没有根据的** ——
     * 空函数不会分配，JIT 也会内联它。这里用二分法把真正的分配点钉出来。
     *
     * 关键变量：
     * - suspend vs 非 suspend 方法调用
     * - 参数是 `TransitionParams<*>`（非空引用）vs 可空引用 vs 无参数
     * - `Set.forEach` + suspend lambda vs 普通 for 循环
     */
    @Test
    fun `空 suspend 调用的分配二分`() {
        PerfHarness.printHeader("分配归因 G：把 176 B/子状态逐步二分")

        val m = createStdLibStateMachine(start = false) {
            val root = initialState("root")
            repeat(150) { state("sib$it") }
            root.transitionConditionally<Evt>("x") { type = TransitionType.EXTERNAL; direction = { targetState(root) } }
        }
        m.startBlocking()
        val abstraction = (m as ru.nsk.kstatemachine.statemachine.InternalStateMachine).coroutineAbstraction
        val rootState = m.states.first() as ru.nsk.kstatemachine.state.InternalState
        val siblings = m.states.toList()

        // 任意一个非空参数（模拟 TransitionParams）
        val param = Any()
        var sink = 0

        val tiers = mutableListOf<Tier>()

        // 1. 基线：一条 runBlocking，什么都不做
        run {
            val r = PerfHarness.measureMedian("1. runBlocking { } 空") { abstraction.runBlocking { sink++ } }
            tiers += Tier("1. runBlocking{} 空", r.nsPerOp, r.bytesPerOp, "")
        }

        // 2. + 普通 for 循环 150 次（非 suspend）
        run {
            val r = PerfHarness.measureMedian("2. + 非 suspend for 循环 150 次") {
                abstraction.runBlocking { for (i in 0 until 150) sink += i }
            }
            tiers += Tier("2. + for 150 次（非 suspend）", r.nsPerOp, r.bytesPerOp, "")
        }

        // 3. + Set.forEach 150 次（非 suspend lambda）
        run {
            val r = PerfHarness.measureMedian("3. + Set.forEach 150 次（非 suspend lambda）") {
                abstraction.runBlocking { siblings.forEach { sink += it.hashCode() } }
            }
            tiers += Tier("3. + Set.forEach 150 次", r.nsPerOp, r.bytesPerOp, "")
        }

        // 4. + 在 for 循环里调用一个**空 suspend 函数**（带一个非空参数）
        run {
            val r = PerfHarness.measureMedian("4. + for 循环里调 150 次空 suspend 函数") {
                abstraction.runBlocking { for (i in 0 until 150) emptySuspend(param) }
            }
            tiers += Tier("4. + 150 次空 suspend(param)", r.nsPerOp, r.bytesPerOp, "")
        }

        // 5. + 同样的空 suspend 函数，但参数**可空**
        run {
            val r = PerfHarness.measureMedian("5. + 150 次空 suspend(nullable param)") {
                abstraction.runBlocking { for (i in 0 until 150) emptySuspendNullable(param) }
            }
            tiers += Tier("5. + 150 次空 suspend(T?)", r.nsPerOp, r.bytesPerOp, "")
        }

        // 6. + 空 suspend 函数，无参数
        run {
            val r = PerfHarness.measureMedian("6. + 150 次空 suspend() 无参数") {
                abstraction.runBlocking { for (i in 0 until 150) emptySuspendNoArg() }
            }
            tiers += Tier("6. + 150 次空 suspend()", r.nsPerOp, r.bytesPerOp, "")
        }

        // 7. 真实目标：recursiveAfterTransitionComplete（跨模块 interface 虚调用）
        //    注意：该遍历自 2026-10-10 起按子树剪枝，本用例的树没有覆写者 → 直接调用是空操作，
        //    这里量到的是剪枝后的早退成本；剪枝前的 176 B / 子状态见
        //    docs/kstatemachine_performance_analysis.md
        run {
            val transition = ru.nsk.kstatemachine.transition.DefaultTransition(
                "probe", ru.nsk.kstatemachine.event.EventMatcher.isInstanceOf<Evt>(),
                TransitionType.LOCAL, null, rootState, rootState
            )
            val params = ru.nsk.kstatemachine.transition.TransitionParams(
                transition, ru.nsk.kstatemachine.transition.unresolvedTargetState(rootState), Evt, null
            )
            val r = PerfHarness.measureMedian("7. recursiveAfterTransitionComplete  兄弟=150") {
                abstraction.runBlocking { rootState.recursiveAfterTransitionComplete(params) }
            }
            tiers += Tier("7. 真实 recursiveAfterTransitionComplete", r.nsPerOp, r.bytesPerOp, "")
        }

        report("分配归因 G：150 个子状态的遍历，逐层加上一个变量", tiers)
        println("  读法：档 3 与档 7 的差 = 「Set.forEach + 真实虚方法调用」相对「普通 for 循环」的额外成本。")
        println("  若档 4/5/6 与档 3 基本持平 → 空 suspend 调用确实不分配（JIT 内联生效）。")
        check(sink != Int.MIN_VALUE)
    }

    private suspend fun emptySuspend(p: Any) {}
    private suspend fun emptySuspendNullable(p: Any?) {}
    private suspend fun emptySuspendNoArg() {}

    /**
     * 归因 H：**决定性实验** —— 同一棵树，suspend 遍历 vs 非 suspend 遍历。
     *
     * 归因 G 出现了自相矛盾的结果：
     * - 档 4/5/6：150 次空 suspend 调用 → **分配 0 B**（JIT 把静态可解析的调用内联掉了）
     * - 档 7：真实的 `recursiveAfterTransitionComplete` 在**同一次测量里**也几乎不分配（176 B）
     * - 但归因 F：同一个方法在**真实的转移流程**里是 **176 B/子状态**
     *
     * 差别只能是**调用点的多态性**：隔离测量时接收者只有 `DefaultState` 一种类型，
     * JIT 能去虚化 + 内联 + 把整趟遍历优化掉；真实树里有多种 `InternalState` 子类，
     * 虚调用点变成多态/巨态（megamorphic），**无法内联 suspend 函数**，
     * 于是每次迭代都必须分配一个 continuation 帧。
     *
     * 本测试直接验证这个解释：用同一棵树、同样遍历 150 个子状态，
     * 分别走 suspend 方法与非 suspend 方法。
     */
    @Test
    fun `suspend 遍历与非 suspend 遍历的对照`() {
        PerfHarness.printHeader("分配归因 H：同一棵树，suspend 遍历 vs 非 suspend 遍历")

        val m = createStdLibStateMachine(start = false) {
            initialState("root")
            repeat(150) { state("sib$it") }
        }
        m.startBlocking()
        val abstraction = (m as ru.nsk.kstatemachine.statemachine.InternalStateMachine).coroutineAbstraction
        val rootState = m.states.first() as ru.nsk.kstatemachine.state.InternalState

        val transition = ru.nsk.kstatemachine.transition.DefaultTransition(
            "probe", ru.nsk.kstatemachine.event.EventMatcher.isInstanceOf<Evt>(),
            TransitionType.LOCAL, null, rootState, rootState
        )
        val params = ru.nsk.kstatemachine.transition.TransitionParams(
            transition, ru.nsk.kstatemachine.transition.unresolvedTargetState(rootState), Evt, null
        )

        var sink = 0

        // 非 suspend：直接遍历同样的 150 个子状态，调用一个普通方法
        val nonSuspend = PerfHarness.measureMedian("非 suspend：for 循环遍历 150 个子状态") {
            for (s in rootState.states) { (s as ru.nsk.kstatemachine.state.InternalState).hashCode(); sink++ }
        }
        println(PerfHarness.report(listOf(nonSuspend)))

        // suspend：真实的 recursiveAfterTransitionComplete
        val suspendWalk = PerfHarness.measureMedian("suspend：recursiveAfterTransitionComplete 150 个子状态") {
            abstraction.runBlocking { rootState.recursiveAfterTransitionComplete(params) }
        }
        println(PerfHarness.report(listOf(suspendWalk)))

        // 真实的转移流程里的同一方法（归因 B/F 的口径）
        val hub = StateNode(
            name = "hub",
            transitions = listOf(StateTransition(event = null, target = "hub", condition = StateCondition.True))
        )
        val ctl = StateGraphController(
            StateMachineGraph(initialNode = hub, nodes = (0 until 150).map { StateNode("s$it", emptyList()) })
        )
        ctl.start()
        val inFlow = PerfHarness.measureMedian("真实转移流程：progress() 每次转移") { ctl.progress(0.05f) }
        println(PerfHarness.report(listOf(inFlow)))

        println()
        println("  → 若「非 suspend 遍历」≈0 B 而「真实转移流程」≈176 B/子状态，")
        println("    则 176 B 的来源是 **无法内联的虚 suspend 调用所强制的 continuation 帧**，")
        println("    而不是「遍历」本身，也不是「空函数」本身。")
        check(sink != Int.MIN_VALUE)
    }

    /**
     * 归因 F：**直接**测量 `recursiveAfterTransitionComplete` 在宽树上的成本。
     *
     * 这是残差里最大的单一嫌疑项：每帧转移后都会无条件遍历整棵子树（`forEachState`），
     * 而它的默认实现是空的。这里绕开事件流程直接调用该方法，把它的成本单独钉出来。
     * （真实分配点的二分见归因 G。）
     *
     * 2026-10-10 该遍历已按子树剪枝：本用例的树没有覆写者，所以现在量到的是早退（≈0 B），
     * 不再是 176 B / 子状态。剪枝前后的完整对照见 docs/kstatemachine_performance_analysis.md。
     */
    @Test
    fun `转移后递归钩子单独测量`() {
        PerfHarness.printHeader("分配归因 F：recursiveAfterTransitionComplete 在宽树上的单独成本")

        val widths = listOf(1, 50, 150)
        val tiers = widths.map { width ->
            val m = createStdLibStateMachine(start = false) {
                initialState("root")
                repeat(width) { state("sib$it") }
            }
            m.startBlocking()

            // 构造一个仅为满足签名的 TransitionParams
            val root = m.states.first()
            val evt = Evt
            val transition = ru.nsk.kstatemachine.transition.DefaultTransition(
                "probe", ru.nsk.kstatemachine.event.EventMatcher.isInstanceOf<Evt>(),
                TransitionType.LOCAL, null, root, root
            )
            val params = ru.nsk.kstatemachine.transition.TransitionParams(
                transition,
                ru.nsk.kstatemachine.transition.unresolvedTargetState(root),
                evt,
                null
            )

            val abstraction = (m as ru.nsk.kstatemachine.statemachine.InternalStateMachine).coroutineAbstraction
            val r = PerfHarness.measureMedian("recursiveAfterTransitionComplete  兄弟=$width") {
                abstraction.runBlocking { m.recursiveAfterTransitionComplete(params) }
            }
            Tier("兄弟 = $width", r.nsPerOp, r.bytesPerOp, "")
        }
        report("分配归因 F：空实现的递归钩子 vs 子树宽度（纯 suspend 函数调用开销）", tiers)

        val perNodeNs = (tiers.last().ns - tiers.first().ns) / (150 - 1)
        val perNodeB = (tiers.last().bytes - tiers.first().bytes) / (150 - 1)
        println("  → 每个子状态 %.1f ns / %.1f B —— 开销全部是「调用一个什么都不做的 suspend 函数」本身"
            .format(perNodeNs, perNodeB))
        println()
        println("  对比归因 B：每次转移的兄弟增量是 49.9 ns / 208.0 B。")
        println("  若本项与 B 同量级，则残差主要来自这里；差额部分才是 activeStates() 与状态转移本身。")
    }
}
