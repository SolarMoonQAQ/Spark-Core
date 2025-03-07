package cn.solarmoon.spark_core.local_control

import cn.solarmoon.spark_core.local_control.LocalController.keyPressTimer
import cn.solarmoon.spark_core.local_control.LocalController.keyRecorder
import com.mojang.serialization.Codec
import net.minecraft.client.KeyMapping

object KeyMappingHelper {

    fun get(name: String) = KeyMapping.ALL[name]

    val CODEC: Codec<KeyMapping> = Codec.STRING.xmap(
        { get(it) },
        { it.name }
    )

}

fun KeyMapping.onEvent(event: KeyEvent, action: (Int) -> Boolean): Boolean {
    when (event) {
        KeyEvent.PRESS -> {
            if (isDown) {
                return action.invoke(getPressTickTime())
            }
            return false
        }
        KeyEvent.PRESS_ONCE -> {
            val isDownLast = keyRecorder.getOrDefault(this, false)
            val isDownNow = isDown
            if (!isDownLast && isDownNow) {
                return action.invoke(0)
            }
            return false
        }
        KeyEvent.RELEASE -> {
            val isDownLast = keyRecorder.getOrDefault(this, false)
            val isDownNow = isDown
            if (isDownLast && !isDownNow) {
                return action.invoke(getPressTickTime())
            }
            return false
        }
        KeyEvent.PULSE -> {
            val isDownLast = keyRecorder.getOrDefault(this, false)
            val isDownNow = isDown
            if (!isDownLast && isDownNow) {
                isDown = false
                return action.invoke(0)
            }
            return false
        }
    }
}

/**
 * 获取当前按键按下的tick总时长
 */
fun KeyMapping.getPressTickTime(): Int = keyPressTimer[this] ?: 0