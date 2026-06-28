package cn.solarmoon.spark_core.compat.sodium

import com.mojang.blaze3d.vertex.VertexConsumer
import org.lwjgl.system.MemoryStack

/**
 * 顶点写入策略接口 —— 抽象 Sodium 与 Embeddium 的顶点格式差异。
 *
 * Sodium 使用 EntityVertex.FORMAT / EntityVertex.write()
 * Embeddium 使用 ModelVertex.FORMAT / ModelVertex.write()
 * 两者方法签名完全一致，仅包路径不同。
 *
 * 具体实现在 [SodiumVertexWriter] 和 [EmbeddiumVertexWriter] 中。
 */
interface VertexWriter {
    /** 单个顶点的字节跨度 */
    val stride: Int

    /** 向 native 缓冲区写入一个顶点 */
    fun emitVertex(
        ptr: Long,
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        overlay: Int,
        light: Int,
        normal: Int
    )

    /** 从 VertexConsumer 提取 VertexBufferWriter，失败返回 null */
    fun getWriter(consumer: VertexConsumer): Any?

    /** 将 scratch buffer 中的顶点批量提交到渲染管线 */
    fun push(writer: Any, stack: MemoryStack, ptr: Long, count: Int)

    /** 将法线打包为 int（Sodium/Embeddium 通用算法） */
    fun packNormal(x: Float, y: Float, z: Float): Int {
        val normX = (x * 127.0f).toInt() and 255
        val normY = (y * 127.0f).toInt() and 255
        val normZ = (z * 127.0f).toInt() and 255
        return (normZ shl 16) or (normY shl 8) or normX
    }
}
