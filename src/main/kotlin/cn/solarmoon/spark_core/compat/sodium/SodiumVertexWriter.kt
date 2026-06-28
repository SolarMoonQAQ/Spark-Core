package cn.solarmoon.spark_core.compat.sodium

import com.mojang.blaze3d.vertex.VertexConsumer
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils
import org.lwjgl.system.MemoryStack

/**
 * Sodium 顶点写入实现 —— 使用 EntityVertex.FORMAT 格式
 */
object SodiumVertexWriter : VertexWriter {

    override val stride: Int = EntityVertex.STRIDE

    override fun emitVertex(
        ptr: Long,
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        overlay: Int,
        light: Int,
        normal: Int
    ) {
        EntityVertex.write(ptr, x, y, z, color, u, v, overlay, light, normal)
    }

    override fun getWriter(consumer: VertexConsumer): Any? {
        return VertexConsumerUtils.convertOrLog(consumer)
    }

    override fun push(writer: Any, stack: MemoryStack, ptr: Long, count: Int) {
        (writer as VertexBufferWriter).push(stack, ptr, count, EntityVertex.FORMAT)
    }
}
