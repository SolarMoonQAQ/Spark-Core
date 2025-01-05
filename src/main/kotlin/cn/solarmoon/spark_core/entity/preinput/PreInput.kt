package cn.solarmoon.spark_core.entity.preinput

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.OnPreInputExecuteEvent
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.common.NeoForge

/**
 * ### 预输入
 * > 装载在[net.minecraft.world.entity.Entity]中的预输入，使用[getPreInput]来获取并修改
 * - 预输入通过id进行标识，每次设置预输入都会覆盖上一个预输入内容
 * - 预输入在生物tick中进行计时，默认存在半秒后会自动清除留存的指令
 * - 预输入不会在游戏中保存，游戏重启后会丢失
 * - 预输入可在双端进行操作，但如果考虑到操作的即时反馈，建议只在客户端进行
 */
class PreInput(
    val holder: Entity
) {

    companion object {
        @JvmStatic
        val DEFAULT_REMAIN_TIME = 10
    }

    var id = ""
        private set
    private var hasInput = false
    private var input = {}
    private var maxRemainTime = DEFAULT_REMAIN_TIME
    private var remain = 0

    /**
     * 是否拥有指定标识符的预输入指令
     * @param id 默认为空，也就是只要指令存在，无论id为什么，都会返回true
     */
    fun hasInput(id: String = ""): Boolean {
        return if (id.isEmpty()) hasInput
        else hasInput && this.id == id
    }

    fun setInput(id: String = "", maxRemainTime: Int = 10, input: () -> Unit) {
        this.input = input
        this.id = id
        this.maxRemainTime = maxRemainTime
        hasInput = true
        remain = 0
    }

    /**
     * 调用预输入指令并清空
     */
    fun execute() {
        val event = NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Pre(holder))
        if (event.isCanceled) return
        input.invoke()
        clear()
        NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Post(holder))
    }

    /**
     * 只在指定预输入存在的情况下调用预输入指令并清空
     * @param id 预输入的标识符，默认为空，也就是只要存在预输入就会执行指令
     */
    fun executeIfPresent(id: String = "", action: () -> Unit = {}): Boolean {
        return if (hasInput(id)) {
            action.invoke()
            execute()
            true
        } else false
    }

    /**
     * 只在id不为[exception]的情况下调用预输入指令并清空
     */
    fun executeExcept(exception: String): Boolean {
        return if (this.id != exception) {
            execute()
            true
        } else false
    }

    /**
     * 清空并重置预输入所有内容
     */
    fun clear() {
        id = ""
        input = {}
        hasInput = false
        remain = 0
        maxRemainTime = DEFAULT_REMAIN_TIME
    }

    /**
     * 只清空指定id的预输入
     */
    fun clearIfPresent(id: String, action: () -> Unit = {}): Boolean {
        return if (hasInput(id)) {
            action.invoke()
            clear()
            true
        } else false
    }

    /**
     * 清空除了指定id以外的预输入
     */
    fun clearExcept(exception: String): Boolean {
        return if (id != exception) {
            clear()
            true
        } else false
    }

    fun tick() {
        if (hasInput && remain < maxRemainTime) remain++
        else {
            clear()
        }
    }

}
