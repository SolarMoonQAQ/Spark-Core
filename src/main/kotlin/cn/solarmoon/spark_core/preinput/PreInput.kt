package cn.solarmoon.spark_core.preinput

import cn.solarmoon.spark_core.event.OnPreInputExecuteEvent
import cn.solarmoon.spark_core.skill.SkillHost
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.properties.Delegates

/**
 * ### 预输入
 * > 装载在[net.minecraft.world.entity.Entity]中的预输入，使用[getPreInput]来获取并修改
 * - 预输入通过id进行标识，每次设置预输入都会覆盖上一个预输入内容
 * - 预输入在生物tick中进行计时，默认存在半秒后会自动清除留存的指令
 * - 预输入不会在游戏中保存，游戏重启后会丢失
 * - 预输入可在双端进行操作，但如果考虑到操作的即时反馈，建议只在客户端进行
 */
class PreInput(
    val holder: IPreInputHolder
) {

    private val inputQueue = ConcurrentLinkedDeque<PreInputData>()
    private val allowedIds = mutableSetOf<String>()
    var isEnabled = true
        private set
    val hasInput get() = inputQueue.isNotEmpty()

    private var cooldown = 0
    private var isPlayingSkill by Delegates.observable(false) { _, old, new -> if (old != new && !new) cooldown = 1 }

    fun hasInput(id: String): Boolean {
        return inputQueue.firstOrNull()?.id == id
    }

    fun setInput(id: String, maxRemainTime: Int = 5, priority: Int = 0, input: () -> Unit) {
        require(maxRemainTime > 0) { "预输入存续时间必须大于0" }
        inputQueue.addFirst(PreInputData(this, id, input, 0, maxRemainTime))
        val temp = inputQueue.sortedByDescending { priority }
        inputQueue.clear()
        inputQueue.addAll(temp)
    }

    fun tryExecute() {
        isPlayingSkill = (holder as? SkillHost)?.isPlayingSkill == true
        if (!isPlayingSkill) {
            if (cooldown > 0) cooldown--
            else execute()
        }
    }

    private fun invoke(data: PreInputData) {
        val event = NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Pre(data))
        if (event.isCanceled || !isInputAllowed(data.id)) return
        data.input.invoke()
        NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Post(data))
    }

    fun execute() {
        inputQueue.pollFirst()?.let {
            invoke(it)
            inputQueue.clear()
        }
    }

    fun executeIfPresent(vararg id: String) {
        inputQueue.firstOrNull { it.id in id }?.let {
            if (isInputAllowed(it.id)) {
                invoke(it)
                inputQueue.clear()
            }
        }
    }

    fun executeExcept(vararg id: String) {
        inputQueue.firstOrNull { it.id !in id }?.let {
            if (isInputAllowed(it.id)) {
                invoke(it)
                inputQueue.clear()
            }
        }
    }

    fun allowInput(id: String) {
        allowedIds.add(id)
    }

    fun disallowInput(id: String) {
        allowedIds.remove(id)
    }

    fun isInputAllowed(id: String): Boolean {
        return isEnabled || id in allowedIds
    }

    fun tick() {
        inputQueue.removeIf { data ->
            data.remain++ >= data.maxRemainTime
        }
    }

    fun clear() {
        inputQueue.clear()
    }

    fun enable() {
        if (!isEnabled) {
            isEnabled = true
            allowedIds.clear()
        }
    }

    fun disable() {
        if (isEnabled) {
            isEnabled = false
        }
    }

}
