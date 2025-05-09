package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.physics.presets.callback.HitData
import net.neoforged.bus.api.Event

/**
 * 自定义受击物理事件，封装受击信息
 */
abstract class HitPhysicsEvent(val hitData: HitData) : Event() {
    class Start(hitData: HitData) : HitPhysicsEvent(hitData)
    class Process(hitData: HitData) : HitPhysicsEvent(hitData)
    class End(hitData: HitData) : HitPhysicsEvent(hitData)
}
