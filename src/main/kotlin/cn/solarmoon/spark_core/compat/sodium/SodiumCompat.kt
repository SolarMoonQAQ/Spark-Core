package cn.solarmoon.spark_core.compat.sodium

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.neoforged.fml.ModList
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * Sodium / Embeddium 兼容层 —— 通过 VertexBufferWriter 批量写入顶点到 native 缓冲，
 * 规避逐顶点 JNI 调用开销。作为 AR (AcceleratedRendering) 未安装时的中间级回退方案。
 *
 * 渲染优先级链：AR > Sodium/Embeddium > 传统 VertexConsumer
 */
object SodiumCompat {

    var IS_LOADED = false
    private var writer: VertexWriter? = null

    /** 每批次最大顶点数 */
    private const val BATCH_SIZE = 256

    /** 临时变量池，避免热路径分配 */
    private val tmpCombinedM4 = Matrix4f()
    private val tmpCombinedNormalM3 = Matrix3f()
    private val tmpNormal = Vector3f()
    private val tmpPos = Vector3f()

    /**
     * 在模组构造时调用，检测 Sodium / Embeddium 是否安装并选择写入策略
     */
    fun init() {
        val modList = ModList.get()
        writer = when {
            modList.isLoaded("sodium") -> SodiumVertexWriter
            modList.isLoaded("embeddium") -> EmbeddiumVertexWriter
            else -> null
        }
        IS_LOADED = writer != null
    }

    // ========================================
    // 骨骼级批量渲染
    // ========================================

    /**
     * 将整个骨骼的所有 Cube 和 Mesh 合批，通过 Sodium/Embeddium 的 VertexBufferWriter 一次性提交。
     *
     * @param bone      要渲染的骨骼
     * @param transform 骨骼的世界变换矩阵（已包含父骨骼 + 动画插值）
     * @param normalMatrix 骨骼的法线变换矩阵
     * @return true 表示已通过 Sodium 路径完成渲染，调用方无需再走传统路径
     */
    fun renderBone(
        bone: OBone,
        transform: Matrix4f,
        normalMatrix: Matrix3f,
        consumer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        val w = writer ?: return false
        val vcWriter = w.getWriter(consumer) ?: return false

        // 提取骨骼世界矩阵分量（内联变换用）
        val bm00 = transform.m00(); val bm01 = transform.m01(); val bm02 = transform.m02(); val bm03 = transform.m03()
        val bm10 = transform.m10(); val bm11 = transform.m11(); val bm12 = transform.m12(); val bm13 = transform.m13()
        val bm20 = transform.m20(); val bm21 = transform.m21(); val bm22 = transform.m22(); val bm23 = transform.m23()

        val nm00 = normalMatrix.m00(); val nm01 = normalMatrix.m01(); val nm02 = normalMatrix.m02()
        val nm10 = normalMatrix.m10(); val nm11 = normalMatrix.m11(); val nm12 = normalMatrix.m12()
        val nm20 = normalMatrix.m20(); val nm21 = normalMatrix.m21(); val nm22 = normalMatrix.m22()

        // 分配 scratch buffer（64字节对齐）
        val scratch = MemoryUtil.nmemAlignedAlloc(64, (BATCH_SIZE * w.stride).toLong())
        var vertexCount = 0
        var ptr = scratch

        try {
            // ---- 1. 渲染所有 Cube ----
            for (cube in bone.cubes) {
                // 合成变换矩阵：骨骼世界 × Cube局部
                val cubeLocal = cube.buildLocalTransformMatrix()
                tmpCombinedM4.set(transform).mul(cubeLocal)
                val cm00 = tmpCombinedM4.m00(); val cm01 = tmpCombinedM4.m01(); val cm02 = tmpCombinedM4.m02(); val cm03 = tmpCombinedM4.m03()
                val cm10 = tmpCombinedM4.m10(); val cm11 = tmpCombinedM4.m11(); val cm12 = tmpCombinedM4.m12(); val cm13 = tmpCombinedM4.m13()
                val cm20 = tmpCombinedM4.m20(); val cm21 = tmpCombinedM4.m21(); val cm22 = tmpCombinedM4.m22(); val cm23 = tmpCombinedM4.m23()

                // 合成法线矩阵
                val cubeLocalNormal = cube.buildLocalNormalMatrix()
                tmpCombinedNormalM3.set(normalMatrix).mul(cubeLocalNormal)
                val cn00 = tmpCombinedNormalM3.m00(); val cn01 = tmpCombinedNormalM3.m01(); val cn02 = tmpCombinedNormalM3.m02()
                val cn10 = tmpCombinedNormalM3.m10(); val cn11 = tmpCombinedNormalM3.m11(); val cn12 = tmpCombinedNormalM3.m12()
                val cn20 = tmpCombinedNormalM3.m20(); val cn21 = tmpCombinedNormalM3.m21(); val cn22 = tmpCombinedNormalM3.m22()

                for (polygon in cube.polygonSet) {
                    // 静态 UV 剔除
                    if (polygon.u1 == 0f && polygon.u2 == 0f &&
                        polygon.v1 == 0f && polygon.v2 == 0f
                    ) continue

                    // 变换法线
                    val nx = cn00 * polygon.normal.x() + cn01 * polygon.normal.y() + cn02 * polygon.normal.z()
                    val ny = cn10 * polygon.normal.x() + cn11 * polygon.normal.y() + cn12 * polygon.normal.z()
                    val nz = cn20 * polygon.normal.x() + cn21 * polygon.normal.y() + cn22 * polygon.normal.z()
                    val packedNormal = w.packNormal(nx, ny, nz)

                    for (vertex in polygon.vertexes) {
                        // 内联矩阵变换位置
                        val px = cm00 * vertex.x + cm01 * vertex.y + cm02 * vertex.z + cm03
                        val py = cm10 * vertex.x + cm11 * vertex.y + cm12 * vertex.z + cm13
                        val pz = cm20 * vertex.x + cm21 * vertex.y + cm22 * vertex.z + cm23

                        w.emitVertex(ptr, px, py, pz, color, vertex.u, vertex.v, overlay, light, packedNormal)
                        ptr += w.stride
                        vertexCount++
                        if (vertexCount >= BATCH_SIZE) {
                            MemoryStack.stackPush().use { stack -> w.push(vcWriter, stack, scratch, vertexCount) }
                            vertexCount = 0
                            ptr = scratch
                        }
                    }
                }
            }

            // ---- 2. 渲染 Mesh（顶点已在骨骼局部空间）----
            bone.mesh?.let { mesh ->
                for (polygon in mesh.polygons) {
                    for (vertex in polygon.vertices) {
                        val vx = vertex.position.x.toFloat()
                        val vy = vertex.position.y.toFloat()
                        val vz = vertex.position.z.toFloat()

                        // 骨骼世界变换
                        val px = bm00 * vx + bm01 * vy + bm02 * vz + bm03
                        val py = bm10 * vx + bm11 * vy + bm12 * vz + bm13
                        val pz = bm20 * vx + bm21 * vy + bm22 * vz + bm23

                        // 法线变换
                        val nvx = vertex.normal.x.toFloat()
                        val nvy = vertex.normal.y.toFloat()
                        val nvz = vertex.normal.z.toFloat()
                        val nx = nm00 * nvx + nm01 * nvy + nm02 * nvz
                        val ny = nm10 * nvx + nm11 * nvy + nm12 * nvz
                        val nz = nm20 * nvx + nm21 * nvy + nm22 * nvz
                        val packedNormal = w.packNormal(nx, ny, nz)

                        w.emitVertex(ptr, px, py, pz, color, vertex.uv.x, vertex.uv.y, overlay, light, packedNormal)
                        ptr += w.stride
                        vertexCount++
                        if (vertexCount >= BATCH_SIZE) {
                            MemoryStack.stackPush().use { stack -> w.push(vcWriter, stack, scratch, vertexCount) }
                            vertexCount = 0
                            ptr = scratch
                        }
                    }
                }
            }

            // 尾批提交
            if (vertexCount > 0) {
                MemoryStack.stackPush().use { stack -> w.push(vcWriter, stack, scratch, vertexCount) }
            }
        } finally {
            MemoryUtil.nmemFree(scratch)
        }
        return true
    }

    // ========================================
    // Cube级批量渲染
    // ========================================

    /**
     * 单个 Cube 的 Sodium 批量渲染 —— 适用于需要逐个 cube 独立渲染的场景。
     * 在 [OCube.renderVertexes] 中作为 AR 检查之后的次级优化路径。
     *
     * @param cube      要渲染的 cube
     * @param poseStack 当前 PoseStack（其 last().pose() 已包含骨骼级变换）
     */
    fun renderCube(
        cube: OCube,
        poseStack: PoseStack,
        consumer: VertexConsumer,
        light: Int,
        overlay: Int,
        color: Int
    ): Boolean {
        val w = writer ?: return false
        val vcWriter = w.getWriter(consumer) ?: return false

        val pose = poseStack.last()
        val boneM4 = pose.pose()   // 已含骨骼级变换
        val boneM3 = pose.normal()

        // 合成变换：骨骼世界 × Cube局部
        val cubeLocal = cube.buildLocalTransformMatrix()
        tmpCombinedM4.set(boneM4).mul(cubeLocal)
        val cm00 = tmpCombinedM4.m00(); val cm01 = tmpCombinedM4.m01(); val cm02 = tmpCombinedM4.m02(); val cm03 = tmpCombinedM4.m03()
        val cm10 = tmpCombinedM4.m10(); val cm11 = tmpCombinedM4.m11(); val cm12 = tmpCombinedM4.m12(); val cm13 = tmpCombinedM4.m13()
        val cm20 = tmpCombinedM4.m20(); val cm21 = tmpCombinedM4.m21(); val cm22 = tmpCombinedM4.m22(); val cm23 = tmpCombinedM4.m23()

        // 合成法线矩阵
        val cubeLocalNormal = cube.buildLocalNormalMatrix()
        tmpCombinedNormalM3.set(boneM3).mul(cubeLocalNormal)
        val cn00 = tmpCombinedNormalM3.m00(); val cn01 = tmpCombinedNormalM3.m01(); val cn02 = tmpCombinedNormalM3.m02()
        val cn10 = tmpCombinedNormalM3.m10(); val cn11 = tmpCombinedNormalM3.m11(); val cn12 = tmpCombinedNormalM3.m12()
        val cn20 = tmpCombinedNormalM3.m20(); val cn21 = tmpCombinedNormalM3.m21(); val cn22 = tmpCombinedNormalM3.m22()

        val batchSize = 256
        val scratch = MemoryUtil.nmemAlignedAlloc(64, (batchSize * w.stride).toLong())
        var vertexCount = 0
        var ptr = scratch

        try {
            for (polygon in cube.polygonSet) {
                // 静态 UV 剔除
                if (polygon.u1 == 0f && polygon.u2 == 0f &&
                    polygon.v1 == 0f && polygon.v2 == 0f
                ) continue

                // 变换法线
                val nx = cn00 * polygon.normal.x() + cn01 * polygon.normal.y() + cn02 * polygon.normal.z()
                val ny = cn10 * polygon.normal.x() + cn11 * polygon.normal.y() + cn12 * polygon.normal.z()
                val nz = cn20 * polygon.normal.x() + cn21 * polygon.normal.y() + cn22 * polygon.normal.z()
                val packedNormal = w.packNormal(nx, ny, nz)

                for (vertex in polygon.vertexes) {
                    // 内联矩阵变换
                    val px = cm00 * vertex.x + cm01 * vertex.y + cm02 * vertex.z + cm03
                    val py = cm10 * vertex.x + cm11 * vertex.y + cm12 * vertex.z + cm13
                    val pz = cm20 * vertex.x + cm21 * vertex.y + cm22 * vertex.z + cm23

                    w.emitVertex(ptr, px, py, pz, color, vertex.u, vertex.v, overlay, light, packedNormal)
                    ptr += w.stride
                    vertexCount++
                    if (vertexCount >= batchSize) {
                        MemoryStack.stackPush().use { stack -> w.push(vcWriter, stack, scratch, vertexCount) }
                        vertexCount = 0
                        ptr = scratch
                    }
                }
            }

            if (vertexCount > 0) {
                MemoryStack.stackPush().use { stack -> w.push(vcWriter, stack, scratch, vertexCount) }
            }
        } finally {
            MemoryUtil.nmemFree(scratch)
        }
        return true
    }
}
