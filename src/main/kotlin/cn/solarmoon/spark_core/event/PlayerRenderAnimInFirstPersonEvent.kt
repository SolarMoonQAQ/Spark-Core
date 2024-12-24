package cn.solarmoon.spark_core.event

import net.minecraft.client.player.AbstractClientPlayer
import net.neoforged.bus.api.Event

/**
 * 此事件用于调控动画是否在第一人称下渲染，比如武器/手臂的挥动等
 */
class PlayerRenderAnimInFirstPersonEvent(
    val player: AbstractClientPlayer
): Event() {

    /**
     * 当此值返回true时，将在第一人称下渲染玩家动作
     */
    var shouldRender: Boolean = false

}