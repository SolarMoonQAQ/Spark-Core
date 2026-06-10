package cn.solarmoon.spark_core.particle.client;

/**
 * 双重缓冲：解耦主线程逻辑更新与渲染线程只读访问。
 * <p>
 * 渲染线程不做运动积分——只做 partialTick lerp 和渲染。
 * 因此双缓冲的核心目的简化为: 防止渲染线程读到半写状态。
 * <p>
 * 线程安全:
 *   - swap() 仅由主线程调用 (tick 结束时)
 *   - getRender() 仅由渲染线程调用 (每帧渲染开始时)
 *   - startTick() 仅由主线程调用 (tick 开始时)
 *   - volatile 保证可见性
 */
public class ParticleDoubleBuffer {

    private final ParticleArray bufferA;
    private final ParticleArray bufferB;

    private volatile ParticleArray renderBuffer;
    private ParticleArray tickBuffer;        // 仅主线程

    public ParticleDoubleBuffer() {
        this(256);
    }

    public ParticleDoubleBuffer(int initialCapacity) {
        this.bufferA = new ParticleArray(initialCapacity);
        this.bufferB = new ParticleArray(initialCapacity);
        this.renderBuffer = bufferA;
        this.tickBuffer = bufferB;
    }

    // ── 由主线程调用 ──

    /**
     * tick 开始时准备写缓冲区。
     * 返回的 tickBuffer 与当前 renderBuffer 相同 (swap 后已同步)。
     * 调用后执行: snapshotPositions() → 逻辑更新 → integrate() → compact()
     *
     * @return tick 缓冲区，用于写入
     */
    public ParticleArray startTick() {
        return tickBuffer;
    }

    /**
     * 原子交换读写缓冲区。
     * volatile 写确保所有写入对渲染线程可见。
     */
    public void swap() {
        ParticleArray oldRender = renderBuffer;
        renderBuffer = tickBuffer;   // volatile 写
        tickBuffer = oldRender;
    }

    // ── 由渲染线程调用 ──

    /**
     * 获取当前渲染缓冲区。
     * 返回 volatile 引用，渲染线程每次读取最新 swap 结果。
     * 仅做只读操作: partialTick lerp + 提交顶点。
     *
     * @return 渲染缓冲区（只读）
     */
    public ParticleArray getRender() {
        return renderBuffer;
    }

    // ── 调试 ──

    /**
     * 获取当前渲染缓冲区中的活跃粒子数。
     */
    public int getActiveCount() {
        return renderBuffer.getCount();
    }
}
