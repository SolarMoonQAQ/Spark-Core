package cn.solarmoon.spark_core.physics.terrain

import cn.solarmoon.spark_core.util.FluidCollisionUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.material.Fluid

/** 字节数组到无符号 int —— `ByteArray` 在 JVM 上是有符号的，值域可能到 255 */
private fun Byte.u(): Int = toInt() and 0xFF

/**
 * Section 级流体索引。
 *
 * 完全独立于 [SectionSnapshot.BlockSnapshot]：流体存在判据是 `FluidState` 非空，
 * 与方块是否有碰撞体积**正交**（含水方块二者皆有，海带/海草只具后者，干石头只具前者）。
 * 因此本结构不参与 `collisionShape` 构建，`BlockMerger` 完全不感知它。
 *
 * ## 布局：256 列主存储 + 稀疏例外
 *
 * 逐 voxel 存储（4096 格 × 高度 + 类型）会为最坏情况付费约 9.5 KB，哪怕只有一滴水。
 * 而绝大多数用途（含 Machine-Max 的流体静力采样点）只需要「某一列的最高水柱」，
 * 逐 voxel 精度只在**同一列存在多层水**时才有意义。故：
 *
 * - 每列的**最高柱**写入 `colTopY/colBottomY/colTopQ/colType`（4 × 256 B = 1 KB）；
 * - 该列**其余**的水体段写入稀疏例外表（按列下标升序，二分查找），多数 section 为空。
 *
 * 实测内存 ≈ 1.2 KB / section（无例外），对比逐 voxel 布局约 7.8 倍收益。
 *
 * ## 线程模型
 *
 * 实例**不可变**：主线程构建，构建完成后只通过替换整个引用发布到物理线程。
 * 严禁原地修改任何数组内容。物理线程只读，无锁。
 *
 * ## 编码约定
 *
 * - 高度量化精度 1/16 块：[Q] = 16，`colTopQ` 取值 1..16，0 保留为「该列无流体」。
 * - **柱底** = 最低含水 voxel 的**底面**（不是它下方那一格），
 *   即 `colBottomY[c]` 是最低含水 voxel 的局部 y。
 * - **柱顶** = `colTopY[c]` voxel 内部的液面，世界 Y = `sectionMinY + colTopY[c] + colTopQ[c] / 16`。
 * - 一段柱内所有 voxel 的 `typeIdx` 必须**相同**（底层熔岩 + 上层水不是一柱，而是两段），
 *   否则 `surfaceY` 会返回错误的液面。
 * - 列索引公式 `lx + (lz shl 4)`，与 `SectionSnapshot.getRelativePosFromIndex` 的 x/z 平面一致。
 *
 * @see [docs/section_fluid_index_plan.md]
 */
class SectionFluidIndex internal constructor(
    /** L1：16 个 y 层的流体存在位掩码，bit i = 局部 y=i 层是否有流体 */
    val layerMask: Int,

    // ---- L2：每列「最高柱」----
    /** 最高柱柱顶 voxel 的局部 y（0..15）；`colTopQ == 0` 时无意义 */
    val colTopY: ByteArray,
    /** 最高柱**最低含水 voxel** 的局部 y（0..15）—— 柱底 = 该 voxel 的底面 */
    val colBottomY: ByteArray,
    /** 最高柱顶面相对柱顶 voxel 底面的高度，1/16 块精度，取值 1..16；0 = 该列无流体 */
    val colTopQ: ByteArray,
    /** 最高柱的流体 palette 下标 + 1；0 = 该列无流体 */
    val colType: ByteArray,

    /** L3：bit c = 第 c 列的最高柱是否续接到上方 section */
    val colReachesTopBits: LongArray,

    /** L5：section 内出现的流体全局 id（`BuiltInRegistries.FLUID`）；下标 0 保留给「无」 */
    val palette: IntArray,

    // ---- L4：稀疏例外（该列存在第二及以下柱），按列下标升序 ----
    /** 段所属列下标（0..255），升序，可重复 */
    val excCol: IntArray,
    /** 段的最低含水 voxel 局部 y */
    val excBottomY: ByteArray,
    /** 段顶 voxel 局部 y */
    val excTopY: ByteArray,
    /** 段顶面 1/16 量化高度（1..16） */
    val excTopQ: ByteArray,
    /** 段的 palette 下标 + 1 */
    val excType: ByteArray
) {

    companion object {
        /** 高度量化精度：1/16 块 */
        const val Q = 16

        /** 列索引：与 `SectionSnapshot.getRelativePosFromIndex` 的 x/z 平面保持一致 */
        @JvmStatic
        fun colIndexOf(lx: Int, lz: Int): Int = lx + (lz shl 4)
    }

    init {
        require(colReachesTopBits.size == 4) { "colReachesTopBits must be LongArray(4) = 256 bit" }
    }

    // ========== 查询（物理线程安全，只读） ==========

    /** 该列是否存在任何流体 */
    fun hasColumn(c: Int): Boolean = colTopQ[c].u() != 0

    /** 该列最高柱是否续接到上方 section（跨 section 续接标记） */
    fun reachesTop(c: Int): Boolean =
        (colReachesTopBits[c shr 6] and (1L shl (c and 63))) != 0L

    /**
     * palette 下标（1-based）→ 全局 fluid id。
     * @return 全局 id；`typeIdx <= 0` 或越界时返回 -1
     */
    fun fluidIdOf(typeIdx: Int): Int {
        if (typeIdx <= 0) return -1
        val i = typeIdx - 1
        return if (i < palette.size) palette[i] else -1
    }

    /**
     * 在稀疏例外表中查找覆盖局部 y = [ly] 的段（二分 + 同列线性扫描）。
     *
     * @return 段下标；未找到返回 -1。段数据通过 [excBottomY]/[excTopY]/[excTopQ]/[excType] 按该下标读取。
     */
    fun findExceptionSegment(c: Int, ly: Int): Int {
        val n = excCol.size
        if (n == 0) return -1
        // 下界二分：找第一个 excCol == c 的下标
        var lo = 0
        var hi = n - 1
        var start = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = excCol[mid]
            if (v < c) {
                lo = mid + 1
            } else if (v > c) {
                hi = mid - 1
            } else {
                start = mid
                hi = mid - 1
            }
        }
        if (start < 0) return -1
        var k = start
        while (k < n && excCol[k] == c) {
            if (ly >= excBottomY[k].u() && ly <= excTopY[k].u()) return k
            k++
        }
        return -1
    }

    /**
     * 判断局部坐标是否浸没。这是全部查询路径的**唯一**实现，
     * 供 `sampleFluid` 与 `sampleFluidBatch` 共用，避免两处逻辑漂移。
     *
     * @param lx/ly/lz 局部坐标（调用方必须已完成 strict 校验，值域 0..15）
     * @param y0 该 section 的最小世界 Y（`sectionY shl 4`）
     * @param wy 采样点的世界 Y（浮点）
     * @param outSurface 长度 ≥ 1 的输出槽位；命中时写入液面世界 Y
     * @return 命中的 fluid 全局 id；**-1 = 该点无流体**
     */
    fun sampleLocal(lx: Int, ly: Int, lz: Int, y0: Int, wy: Double, outSurface: FloatArray): Int {
        if (layerMask and (1 shl ly) == 0) return -1

        val c = lx + (lz shl 4)
        val topQ = colTopQ[c].u()
        if (topQ == 0) return -1

        val topY = colTopY[c].u()
        if (ly > topY) return -1                    // 在最高柱之上 → 无流体

        val botY = colBottomY[c].u()
        if (ly >= botY) {
            // 命中主柱（O(1) 路径）
            val surfaceY = y0 + topY + topQ / Q.toFloat()
            if (wy >= surfaceY) return -1           // 在液面之上
            outSurface[0] = surfaceY
            return fluidIdOf(colType[c].u())
        }

        // 落在最高柱之下：可能是悬空水洼下方的另一段水体（罕见路径）
        val k = findExceptionSegment(c, ly)
        if (k < 0) return -1
        val surfaceY = y0 + excTopY[k].u() + excTopQ[k].u() / Q.toFloat()
        if (wy >= surfaceY) return -1
        outSurface[0] = surfaceY
        return fluidIdOf(excType[k].u())
    }

    /**
     * 构建期收集器。**主线程独占**，不可跨线程共享。
     *
     * 遍历期用两张 4096 字节的 transient 数组暂存逐 voxel 数据（惰性分配：
     * 第一个流体 voxel 出现时才分配），`build()` 结束后即丢弃，
     * 因此只有真正含流体的 section 才会付这 8 KB 的瞬时成本。
     */
    class Builder {

        private var layerMask = 0
        private var any = false
        private val palette = ArrayList<Fluid>(2)
        private var voxQ: ByteArray? = null
        private var voxType: ByteArray? = null
        private var colReachesTopBits = LongArray(4)

        /** 记录一个含流体的 voxel。@param topQ 已量化的顶面高度（1..16） */
        fun mark(x: Int, y: Int, z: Int, fluid: Fluid, topQ: Int) {
            var q = voxQ
            var t = voxType
            if (q == null || t == null) {
                q = ByteArray(4096)
                t = ByteArray(4096)
                voxQ = q
                voxType = t
            }
            val i = x + (y shl 4) + (z shl 8)
            q[i] = topQ.coerceIn(1, Q).toByte()

            var pi = palette.indexOf(fluid)     // Fluid 未覆写 equals → 身份比较；palette 通常 ≤ 4 项
            if (pi < 0) {
                palette.add(fluid)
                pi = palette.size - 1
            }
            t[i] = (pi + 1).toByte()

            layerMask = layerMask or (1 shl y)
            any = true
        }

        /**
         * 标记某列的最高柱续接到上方 section。
         * 判据必须由调用方给出（局部 y == 15 且上方下一 section 最低格为**同类**流体）。
         * **不得**用 `topQ == 16` 判定 —— 静水顶层是 8/9，量化后为 14，该判据会永远为 false。
         */
        fun markReachesTop(x: Int, z: Int) {
            val c = x + (z shl 4)
            colReachesTopBits[c shr 6] = colReachesTopBits[c shr 6] or (1L shl (c and 63))
        }

        /** 是否存在任何流体（决定 `SectionSnapshot` 是否为 EMPTY） */
        fun hasAny(): Boolean = any

        /**
         * 产出不可变索引；无流体时返回 null。
         *
         * 必须在主线程调用（会解析 `BuiltInRegistries.FLUID` 的 id 并检查流体属性表）。
         */
        fun build(): SectionFluidIndex? {
            if (!any) return null
            val q = voxQ ?: return null
            val t = voxType ?: return null

            val colTopY = ByteArray(256)
            val colBottomY = ByteArray(256)
            val colTopQ = ByteArray(256)
            val colType = ByteArray(256)

            // 例外表（可增长的暂存区，最后 copyOf 精确长度）
            var excCap = 32
            var excCount = 0
            var aCol = IntArray(excCap)
            var aBot = ByteArray(excCap)
            var aTop = ByteArray(excCap)
            var aQ = ByteArray(excCap)
            var aType = ByteArray(excCap)

            for (z in 0 until 16) {
                for (x in 0 until 16) {
                    val c = x + (z shl 4)
                    var y = 15
                    var seg = 0
                    while (y >= 0) {
                        val i = x + (y shl 4) + (z shl 8)
                        val qq = q[i].toInt() and 0xFF
                        if (qq == 0) {
                            y--
                            continue
                        }
                        val tt = t[i]
                        // 向下延伸，求同类型连续段的最低 voxel
                        var yb = y
                        while (yb > 0) {
                            val ib = x + ((yb - 1) shl 4) + (z shl 8)
                            if ((q[ib].toInt() and 0xFF) == 0 || t[ib] != tt) break
                            yb--
                        }

                        if (seg == 0) {
                            // 自顶向下遇到的第一段即最高柱
                            colTopY[c] = y.toByte()
                            colBottomY[c] = yb.toByte()
                            colTopQ[c] = qq.toByte()
                            colType[c] = tt
                        } else {
                            if (excCount == excCap) {
                                excCap *= 2
                                aCol = aCol.copyOf(excCap)
                                aBot = aBot.copyOf(excCap)
                                aTop = aTop.copyOf(excCap)
                                aQ = aQ.copyOf(excCap)
                                aType = aType.copyOf(excCap)
                            }
                            aCol[excCount] = c
                            aBot[excCount] = yb.toByte()
                            aTop[excCount] = y.toByte()
                            aQ[excCount] = qq.toByte()
                            aType[excCount] = tt
                            excCount++
                        }
                        seg++
                        y = yb - 1
                    }
                }
            }

            // palette → 全局 fluid id（主线程，注册表访问）
            val ids = IntArray(palette.size)
            for (i in palette.indices) {
                ids[i] = BuiltInRegistries.FLUID.getId(palette[i])
            }
            // 首次构建时同步建立流体属性表，保证物理线程拿到 id 前表已就绪（P1-4）
            FluidCollisionUtil.ensureBuilt()

            return SectionFluidIndex(
                layerMask = layerMask,
                colTopY = colTopY,
                colBottomY = colBottomY,
                colTopQ = colTopQ,
                colType = colType,
                colReachesTopBits = colReachesTopBits,
                palette = ids,
                excCol = aCol.copyOf(excCount),
                excBottomY = aBot.copyOf(excCount),
                excTopY = aTop.copyOf(excCount),
                excTopQ = aQ.copyOf(excCount),
                excType = aType.copyOf(excCount)
            )
        }
    }
}
