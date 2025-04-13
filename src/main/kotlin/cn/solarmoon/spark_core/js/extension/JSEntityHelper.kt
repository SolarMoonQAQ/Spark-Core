package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.entity.getRelativeVector
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.mozilla.javascript.Function
import kotlin.math.PI
import kotlin.math.atan2

object JSEntityHelper: JSComponent() {

    @HostAccess.Export
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

    @HostAccess.Export
    fun preventLocalInput(event: MovementInputUpdateEvent, consumer: Function) {
        val player = event.entity as LocalPlayer
        consumer.call(engine, event.input, player)
    }

}