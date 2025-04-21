package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import net.minecraft.client.player.LocalPlayer
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import org.mozilla.javascript.Function

object JSEntityHelper: JSComponent() {

    fun preventLocalInput(event: MovementInputUpdateEvent) {
        val player = event.entity as LocalPlayer
        event.input.apply {
            forwardImpulse = 0f
            leftImpulse = 0f
            up = false
            down = false
            left = false
            right = false
            jumping = false
            shiftKeyDown = false
            player.sprintTriggerTime = -1
            player.swinging = false
        }
    }

    fun preventLocalInput(event: MovementInputUpdateEvent, consumer: Function) {
        val player = event.entity as LocalPlayer
        consumer.call(engine, event.input, player)
    }

}