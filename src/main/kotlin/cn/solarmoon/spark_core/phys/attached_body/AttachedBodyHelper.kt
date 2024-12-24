package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.attachment.IAttachmentHolder

object AttachedBodyHelper {
}

fun IAttachmentHolder.getBodies() = getData(SparkAttachments.BODY)

fun IAttachmentHolder.putBody(body: AttachedBody) {
    getBodies()[body.name] = body
}

fun IAttachmentHolder.getBody(name: String) = getBodies()[name]

inline fun <reified B> IAttachmentHolder.getBody(name: String): B? {
    return getBodies()[name] as? B
}

fun Entity.getBoundingBoxBody() = getBody<EntityBoundingBoxBody>("body")

fun Entity.removeBoundingBoxBody() {
    getBoundingBoxBody()?.let {
        getBodies().remove("body")
    }
}