package cn.solarmoon.spark_core.event

import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.attachment.IAttachmentHolder

abstract class OnPreInputExecuteEvent(
    val holder: Entity
): Event() {

    class Pre(holder: Entity): OnPreInputExecuteEvent(holder), ICancellableEvent

    class Post(holder: Entity): OnPreInputExecuteEvent(holder)

}