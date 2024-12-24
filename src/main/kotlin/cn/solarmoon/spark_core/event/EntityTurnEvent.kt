package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.event.entity.EntityEvent

/**
 * 该事件在生物尝试旋转头部时调用 [Entity.turn]
 *
 * 对于客户端而言，它同时也意味着客户端侧转动摄像机后尝试转动玩家视角到摄像机角度的时候
 */
class EntityTurnEvent(entity: Entity, val xRot: Double, val yRot: Double): EntityEvent(entity), ICancellableEvent {



}