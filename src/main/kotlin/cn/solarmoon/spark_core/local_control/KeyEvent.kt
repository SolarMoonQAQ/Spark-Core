package cn.solarmoon.spark_core.local_control

import com.mojang.serialization.Codec

enum class KeyEvent {
    PRESS, PRESS_ONCE, RELEASE;

    companion object {
        val CODEC: Codec<KeyEvent> = Codec.STRING.xmap(
            { KeyEvent.valueOf(it.uppercase()) },
            { it.toString().lowercase() }
        )
    }
}