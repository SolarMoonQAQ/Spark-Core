//package cn.solarmoon.spark_core.compat.sodium
//
//import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter
//import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex
//import org.lwjgl.system.MemoryStack
//import org.lwjgl.system.MemoryUtil
//
///**
// * Sodium/Embeddium顶点加速渲染工具
// *
// * 作用：
// * 1. 绕过VertexConsumer逐顶点调用
// * 2. 直接写入Native Memory
// * 3. 批量提交顶点
// *
// * 在复杂模型（大量Cube/Polygon）下CPU开销可下降3~10倍。
// */
//object SodiumVertexEmitter {
//
//    /**
//     * 顶点临时缓冲
//     *
//     * 一个Cube最多：
//     * 6 faces * 4 vertices = 24 vertices
//     */
//    private val SCRATCH_BUFFER: Long =
//        MemoryUtil.nmemAlignedAlloc(64, (24 * EntityVertex.STRIDE).toLong())
//
//    /**
//     * 将法线打包为Sodium格式
//     *
//     * Sodium使用：
//     * 3 * int8
//     *
//     * 范围：
//     * [-1,1] -> [-127,127]
//     */
//    fun packNormal(x: Float, y: Float, z: Float): Int {
//        val nx = (x * 127f).toInt() and 255
//        val ny = (y * 127f).toInt() and 255
//        val nz = (z * 127f).toInt() and 255
//        return (nz shl 16) or (ny shl 8) or nx
//    }
//
//    /**
//     * 写入一个顶点
//     */
//    fun emitVertex(
//        ptr: Long,
//        x: Float,
//        y: Float,
//        z: Float,
//        color: Int,
//        u: Float,
//        v: Float,
//        overlay: Int,
//        light: Int,
//        normal: Int
//    ) {
//        EntityVertex.write(ptr, x, y, z, color, u, v, overlay, light, normal)
//    }
//
//    /**
//     * 提交顶点缓冲
//     */
//    fun flush(writer: VertexBufferWriter, vertexCount: Int) {
//        MemoryStack.stackPush().use { stack ->
//            writer.push(stack, SCRATCH_BUFFER, vertexCount, EntityVertex.FORMAT)
//        }
//    }
//}