package cn.solarmoon.spark_core.event

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.attachment.IAttachmentHolder

abstract class OnPreInputExecuteEvent(
    val holder: IAttachmentHolder
): Event() {

    class Pre(holder: IAttachmentHolder): OnPreInputExecuteEvent(holder), ICancellableEvent

    class Post(holder: IAttachmentHolder): OnPreInputExecuteEvent(holder)

}