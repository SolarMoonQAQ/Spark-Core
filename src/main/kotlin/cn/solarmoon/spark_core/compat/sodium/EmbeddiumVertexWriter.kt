package cn.solarmoon.spark_core.compat.sodium

import com.mojang.blaze3d.vertex.VertexConsumer
import org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter
import org.embeddedt.embeddium.api.vertex.format.common.ModelVertex
import org.embeddedt.embeddium.impl.render.vertex.VertexConsumerUtils
import org.lwjgl.system.MemoryStack

/**
 * Embeddium 顶点写入实现 —— 使用 ModelVertex.FORMAT 格式
 */
object EmbeddiumVertexWriter : VertexWriter {

    override val stride: Int = ModelVertex.STRIDE

    override fun emitVertex(
        ptr: Long,
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        overlay: Int,
        light: Int,
        normal: Int
    ) {
        ModelVertex.write(ptr, x, y, z, color, u, v, overlay, light, normal)
    }

    override fun getWriter(consumer: VertexConsumer): Any? {
        return VertexConsumerUtils.convertOrLog(consumer)
    }

    override fun push(writer: Any, stack: MemoryStack, ptr: Long, count: Int) {
        (writer as VertexBufferWriter).push(stack, ptr, count, ModelVertex.FORMAT)
    }
}
