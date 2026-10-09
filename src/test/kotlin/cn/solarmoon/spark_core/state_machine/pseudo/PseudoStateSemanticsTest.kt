package cn.solarmoon.spark_core.state_machine.pseudo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.event.WrappedEvent
import ru.nsk.kstatemachine.state.HistoryState
import ru.nsk.kstatemachine.state.HistoryType
import ru.nsk.kstatemachine.state.DefaultState
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.findState
import ru.nsk.kstatemachine.state.historyState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.ProcessingResult
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.buildCreationArguments
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onTransitionComplete
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.statemachine.stopBlocking
import ru.nsk.kstatemachine.statemachine.undoBlocking

/**
 * `UndoState` 与 `DefaultHistoryState` 的语义测试。
 *
 * ## 为什么需要这个文件
 *
 * 这两个类是**全代码库中唯一覆写 `recursiveAfterTransitionComplete` 的地方**
 * （见 [`docs/kstatemachine_after_complete_audit.md`](../../../../../../../docs/kstatemachine_after_complete_audit.md)）。
 * 该回调目前没有测试覆盖，而针对它的性能优化（剪枝整树遍历）**必须**先有语义安全网。
 *
 * | 被测类 | 覆写内容 | 影响面 |
 * |---|---|---|
 * | `UndoState` | 每次转移完成后把 (目标状态, 原始事件) 压入回退栈 | `undo()` 的全部行为 |
 * | `DefaultHistoryState` | 记录父状态最后所处的子状态；DEEP 模式下深挖到叶 | `historyState()` 的全部行为 |
 *
 * 两者都是 `BasePseudoState` 的子类，因此**永远没有子状态**
 * （`BasePseudoState.addState` 直接抛异常）—— 这正是剪枝不会误伤它们的原因，
 * 本文件用测试把这一前提固定下来。
 */
class PseudoStateSemanticsTest {

    private object Go : Event
    private object Back : Event
    private object Step : Event

    private object Probe : Event
    private object Cancel : Event
    private object Resume : Event

    // ══════════════════════════════════════════════════════════
    // UndoState
    // ══════════════════════════════════════════════════════════

    private fun undoMachine(): StateMachine =
        createStdLibStateMachine(
            start = false,
            creationArguments = buildCreationArguments { isUndoEnabled = true }
        ) {
            val a = initialState("a")
            val b = state("b")
            val c = state("c")
            a.transition<Go>("a->b") { targetState = b }
            b.transition<Go>("b->c") { targetState = c }
        }

    @Test
    fun `undo 回到上一个状态`() {
        val m = undoMachine()
        m.startBlocking()
        assertEquals("a", m.activeStateName())

        m.processEventBlocking(Go)
        assertEquals("b", m.activeStateName())

        val result = m.undoBlocking()
        assertEquals(ProcessingResult.PROCESSED, result, "undo 应被视为已处理")
        assertEquals("a", m.activeStateName(), "undo 应回到上一个状态 a")
    }

    @Test
    fun `连续 undo 逐级回退`() {
        val m = undoMachine()
        m.startBlocking()

        m.processEventBlocking(Go) // a -> b
        m.processEventBlocking(Go) // b -> c
        assertEquals("c", m.activeStateName())

        m.undoBlocking() // c -> b
        assertEquals("b", m.activeStateName(), "第一次 undo 应回到 b")

        m.undoBlocking() // b -> a
        assertEquals("a", m.activeStateName(), "第二次 undo 应回到 a")
    }

    /**
     * 回退栈不足时 `UndoState.popTargetStates()` 返回空集合 → 不产生转移 → 返回 `IGNORED`。
     *
     * **实测行为**：启动后栈只有 1 条记录（`start(a)`），`popTargetStates()` 检测到
     * `stack.size >= 2` 不成立就返回 `emptySet()`，于是 `TargetState` 为空集合 →
     * `doProcessEvent` 认为没有转移 → `IGNORED`。
     * 关键要求是**不能抛异常**，且机器之后仍然可用。
     */
    @Test
    fun `无可回退历史时 undo 被忽略且不抛异常`() {
        val m = undoMachine()
        m.startBlocking()

        // 启动只压入一条记录，栈大小 1 → popTargetStates() 返回 emptySet()
        val result = m.undoBlocking()
        assertEquals(ProcessingResult.IGNORED, result, "无历史可回退时应返回 IGNORED（未产生转移）")
        assertEquals("a", m.activeStateName(), "无历史可回退时应停留在原地")

        // 之后机器仍应可正常工作
        m.processEventBlocking(Go)
        assertEquals("b", m.activeStateName(), "undo 之后机器应仍然可用")
    }

    /**
     * **剪枝安全性核心断言**：`UndoState` 的钩子必须在**每一次**转移后被调用。
     *
     * 如果按 `needsAfterTransitionComplete` 剪枝时漏掉了 `UndoState`，
     * 回退栈就不会增长，`undo()` 会静默退化成空操作 —— 本测试会立刻失败。
     */
    @Test
    fun `每次转移后 UndoState 钩子都被调用`() {
        val m = undoMachine()
        m.startBlocking()

        // 建立 3 条历史：start(a), a->b, b->c
        m.processEventBlocking(Go)
        m.processEventBlocking(Go)
        assertEquals("c", m.activeStateName())

        // 若钩子被漏掉，栈不足 2 条，第一次 undo 就会原地不动
        m.undoBlocking()
        assertNotEquals("c", m.activeStateName(), "钩子若未被调用，undo 会退化为空操作")
        assertEquals("b", m.activeStateName())

        m.undoBlocking()
        assertEquals("a", m.activeStateName())
    }

    /** 未启用 undo 时不该有 `UndoState`，`undo()` 应抛异常 */
    @Test
    fun `未启用 undo 时不存在 UndoState`() {
        val m = createStdLibStateMachine(start = false) { initialState("a") }
        m.startBlocking()
        assertTrue(m.findState("undoState") == null, "未启用 undo 时不应创建 UndoState 状态")

        val e = assertThrows<IllegalStateException> { m.undoBlocking() }
        assertTrue(e.message!!.contains("Undo functionality is not enabled"), "实际信息：${e.message}")
    }

    /** 启用 undo 时 `UndoState` 确实存在，且是一个纯状态节点（无子状态） */
    @Test
    fun `启用 undo 时 UndoState 存在且没有子状态`() {
        val m = undoMachine()
        m.startBlocking()

        val undo = m.findState("undoState")
        assertTrue(undo != null, "启用 undo 后应存在 undoState")
        assertTrue(undo!!.states.isEmpty(), "伪状态必须没有子状态（剪枝安全性的前提）")
    }

    /**
     * `undo` 应把**原始事件**包装后交给 undo 转移。
     *
     * ⚠️ **实测行为与直觉不同**：`UndoState.makeWrappedEvent()` 取的是
     * `stack[stack.size - 2]` —— 即**倒数第二条**记录。所以在
     * `start(a) → a-Go->b` 之后执行 undo，拿到的是**启动转移**的原始事件
     * （`StartEventImpl`），而不是刚才那次 `Go`。
     *
     * 本测试把这个实际行为固定下来（而不是断言"应该"是什么），
     * 这样任何剪枝改动一旦影响 `UndoState` 的记录时机，都会立刻暴露。
     */
    @Test
    fun `undo 转移的事件来自倒数第二条历史记录`() {
        val seen = mutableListOf<Event>()
        val m = createStdLibStateMachine(
            start = false,
            creationArguments = buildCreationArguments { isUndoEnabled = true }
        ) {
            val a = initialState("a")
            val b = state("b")
            a.transition<Go>("a->b") { targetState = b }
        }
        m.onTransitionComplete { _, params -> seen += params.event }
        m.startBlocking()

        seen.clear()
        m.processEventBlocking(Go) // a -> b
        assertEquals(listOf<Event>(Go), seen, "普通转移应看到原始事件 Go")

        seen.clear()
        m.undoBlocking() // b -> a
        assertEquals(1, seen.size, "undo 应产生一次转移")
        val event = seen.single()
        assertTrue(
            event is WrappedEvent,
            "undo 转移的事件应是 WrappedEvent，实际：${event::class.simpleName}"
        )
        assertTrue(
            (event as WrappedEvent).event is ru.nsk.kstatemachine.event.StartEvent,
            "实测：包装内是倒数第二条记录（启动转移的 StartEvent），实际：${event.event::class.simpleName}"
        )
    }

    /**
     * `UndoState` 明确忽略由它自己产生的 `WrappedEvent` 转移（避免自我记录）。
     * 若这条判断失效，回退栈会被污染，连续 undo 会错乱。
     */
    @Test
    fun `UndoState 不记录自己产生的 WrappedEvent 转移`() {
        val m = undoMachine()
        m.startBlocking()
        m.processEventBlocking(Go) // a -> b

        // 连续两次 undo：第二次必须还能回到更早的状态，说明第一次 undo 没有污染栈
        m.undoBlocking() // b -> a
        m.undoBlocking() // a -> 无历史，原地不动
        assertEquals("a", m.activeStateName(), "第一次 undo 产生的 WrappedEvent 不应被记录进回退栈")
    }

    // ══════════════════════════════════════════════════════════
    // DefaultHistoryState —— SHALLOW
    // ══════════════════════════════════════════════════════════

    /**
     * SHALLOW 历史机：外层 `on` 内部有 `sub1` / `sub2`，外加一个 `historyState`。
     *
     * 结构：
     * ```
     * machine (EXCLUSIVE)
     *   ├── off            (initial)
     *   └── on             (EXCLUSIVE, initial = sub1)
     *         ├── sub1
     *         ├── sub2
     *         └── hist     (HistoryState, SHALLOW)
     * ```
     */
    private fun shallowHistoryMachine(): StateMachine =
        createStdLibStateMachine(start = false) {
            val off = initialState("off")
            val on = state("on") {
                val sub1 = initialState("sub1")
                val sub2 = state("sub2")
                historyState("hist", historyType = HistoryType.SHALLOW)
                sub1.transition<Step>("sub1->sub2") { targetState = sub2 }
            }
            off.transition<Resume>("off->on") { targetState = on }
            on.transition<Probe>("on->off") {
                targetState = off
            }
        }

    @Test
    fun `SHALLOW 历史状态没有子状态`() {
        val m = shallowHistoryMachine()
        m.startBlocking()

        val hist = m.findState<HistoryState>()
        assertTrue(hist != null, "应能找到 historyState")
        assertTrue((hist as IState).states.isEmpty(), "伪状态必须没有子状态（剪枝安全性的前提）")
        assertEquals(HistoryType.SHALLOW, hist.historyType)
    }

    @Test
    fun `historyState 的默认状态取父状态的初始状态`() {
        val m = shallowHistoryMachine()
        m.startBlocking()

        val hist = m.findState<HistoryState>()!!
        assertEquals(
            "sub1", hist.defaultState.name,
            "未显式指定 defaultState 时应回落到父状态的初始状态"
        )
    }

    @Test
    fun `SHALLOW 历史记录父状态最后所处的子状态`() {
        val m = shallowHistoryMachine()
        m.startBlocking()
        assertEquals("off", m.activeStateName())

        // off -> on (进入 sub1)
        m.processEventBlocking(Resume)
        assertEquals("sub1", m.activeStateName())

        // on 内部 sub1 -> sub2
        m.processEventBlocking(Step)
        assertEquals("sub2", m.activeStateName())

        val hist = m.findState<HistoryState>()!!
        assertEquals("sub2", hist.storedState.name, "父状态当前子状态变为 sub2 后应被记录")

        // on -> off
        m.processEventBlocking(Probe)
        assertEquals("off", m.activeStateName())
        assertEquals("sub2", hist.storedState.name, "离开 on 后历史记录应保留 sub2")
    }

    /**
     * **剪枝安全性核心断言**：`DefaultHistoryState` 的钩子若被漏掉，
     * `storedState` 就不会更新 —— 本测试会立刻失败。
     */
    @Test
    fun `每次转移后 DefaultHistoryState 钩子都被调用`() {
        val m = shallowHistoryMachine()
        m.startBlocking()
        val hist = m.findState<HistoryState>()!!

        m.processEventBlocking(Resume) // off -> on，父状态切换到 sub1
        assertEquals("sub1", hist.storedState.name, "钩子若未被调用，storedState 会停留在初始值")

        m.processEventBlocking(Step)   // sub1 -> sub2
        assertEquals("sub2", hist.storedState.name, "钩子若未被调用，storedState 不会跟随更新")
    }

    /**
     * **停止时的历史清理** —— 实测行为比直觉微妙，值得固定下来。
     *
     * 停止流程是：
     * 1. `processStep1` 收到 `StopEvent` → 调 `doStop()`
     * 2. `doStop()` → `recursiveStop()` → 各状态 `onStopped()` → **历史被清空**
     * 3. 但 `processStep1` 返回后，`doProcessEvent` 仍会执行
     *    `recursiveAfterTransitionComplete(...)`（StopEvent 也走完整转移流程）
     * 4. 该回调把**停止转移的目标状态**（= 机器的初始状态）**重新写入** `storedState`
     *
     * 所以 `onStopped()` 的清空会被随后的回调覆盖。本测试锁定这个顺序。
     */
    @Test
    fun `停止时 onStopped 清空历史但随后被停止转移重新记录`() {
        val m = shallowHistoryMachine()
        m.startBlocking()
        m.processEventBlocking(Resume)
        m.processEventBlocking(Step)

        val hist = m.findState<HistoryState>()!!
        assertEquals("sub2", hist.storedState.name, "停止前记录的是 sub2")

        m.stopBlocking()
        assertEquals(
            "sub1", hist.storedState.name,
            "停止转移的目标是机器初始状态 off；off 不含历史状态，" +
                    "故 storedState 保持 onStopped 清空后的 defaultState(sub1)"
        )
    }

    // ══════════════════════════════════════════════════════════
    // DefaultHistoryState —— DEEP
    // ══════════════════════════════════════════════════════════

    /**
     * DEEP 历史机：`on -> level2 -> level3`，历史状态挂在 `level2` 内。
     * DEEP 模式应在 `level3` 处于活跃时把记录下探到 `level3`。
     */
    private fun deepHistoryMachine(): StateMachine =
        createStdLibStateMachine(start = false) {
            val off = initialState("off")
            val on = state("on") {
                val l2 = initialState("level2") {
                    val l3a = initialState("level3a")
                    val l3b = state("level3b")
                    historyState("hist", historyType = HistoryType.DEEP)
                    l3a.transition<Step>("l3a->l3b") { targetState = l3b }
                }
            }
            off.transition<Resume>("off->on") { targetState = on }
            on.transition<Probe>("on->off") { targetState = off }
        }

    @Test
    fun `DEEP 历史记录下探到最深活跃叶`() {
        val m = deepHistoryMachine()
        m.startBlocking()

        m.processEventBlocking(Resume) // off -> on -> level2 -> level3a
        assertEquals("level3a", m.activeStateName())

        val hist = m.findState<HistoryState>()!!
        assertEquals(HistoryType.DEEP, hist.historyType)

        m.processEventBlocking(Step)   // level3a -> level3b
        assertEquals("level3b", m.activeStateName())
        assertEquals(
            "level3b", hist.storedState.name,
            "DEEP 模式下 targetState(level3b) 是 storedState(level2) 的子状态，应把记录下探到 level3b"
        )
    }

    /**
     * DEEP 模式下的停止行为 —— 与 SHALLOW 不同，这里能**观察到**清空被覆盖。
     *
     * 停止前 `storedState = level3b`；`onStopped()` 清空后回落 `defaultState = level2`；
     * 随后停止转移的 `recursiveAfterTransitionComplete` 又把
     * `direction.targetState`（= `on`，是 `level2` 的子状态）写回，于是变成 `level3a`
     * （`on` 的初始子状态，由 `targetState.isSubStateOf(level2)` 判定通过）。
     *
     * 这是 `DefaultHistoryState` 在 DEEP 模式下的真实行为，**不是**"干净地回到默认态"。
     */
    @Test
    fun `DEEP 历史在停止后被停止转移重新记录`() {
        val m = deepHistoryMachine()
        m.startBlocking()
        m.processEventBlocking(Resume)
        m.processEventBlocking(Step)

        val hist = m.findState<HistoryState>()!!
        assertEquals("level3b", hist.storedState.name, "停止前记录的是最深的 level3b")

        m.stopBlocking()
        assertEquals(
            "level3a", hist.storedState.name,
            "停止转移把 on 重新写入记录；on 是 level2 的子状态，DEEP 判定通过 → 记录下探到 level3a"
        )
    }

    // ══════════════════════════════════════════════════════════
    // 剪枝安全性的结构性前提
    // ══════════════════════════════════════════════════════════

    /**
     * 剪枝方案依赖的关键前提：**伪状态永远没有子状态**。
     *
     * `BasePseudoState.addState` 直接抛 `UnsupportedOperationException`，
     * 所以递归在伪状态处天然短路 —— 剪枝只影响「是否向下递归」，
     * 不影响「当前节点是否执行钩子」。
     */
    @Test
    fun `伪状态禁止添加子状态`() {
        val m = shallowHistoryMachine()
        m.startBlocking()
        val hist = m.findState<HistoryState>()!! as IState

        val e = assertThrows<UnsupportedOperationException> {
            hist.addState(DefaultState("illegal_child"))
        }
        assertTrue(
            e.message!!.contains("can not have child states"),
            "伪状态必须禁止子状态，实际信息：${e.message}"
        )
    }
}

/**
 * 当前**最深活跃叶状态**的 name。
 *
 * 注意不能用 `activeStates().first()` —— 它返回的是 `Set`，顺序不确定。
 * 活跃路径是一条链（EXCLUSIVE），所以「没有活跃子状态的那个」才是叶。
 */
private fun StateMachine.activeStateName(): String? =
    activeStates()
        .firstOrNull { state -> state.states.none { it.isActive } }
        ?.name
