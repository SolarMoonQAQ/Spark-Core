package cn.solarmoon.spark_core.particle.client;

import java.util.Random;

/**
 * SoA (Structure of Arrays) 粒子存储。
 * <p>
 * 所有粒子字段按类型分解为独立数组，更新/渲染时顺序遍历同一数组，
 * 利用 CPU 缓存线预取，大幅减少缓存未命中。
 * <p>
 * 对比 AoS (List<ParticleInstance>):
 *   AoS: 每粒子对象 ~30 字段 ≈ 200+ 字节 → 每条缓存线仅 ~3 粒子
 *   SoA: 16 floats/缓存线 → 一次 posX[i] 遍历拉 16 粒子入 L1
 * <p>
 * 线程安全约定:
 *   - tick 线程: 写入所有数组 (独占)
 *   - render 线程: 只读所有数组 (通过 volatile swap 确保可见性)
 *   - 无并发写竞争 (同一时刻只有一线程持有写入权)
 */
public class ParticleArray {

    private static final int INITIAL_CAPACITY = 256;
    private static final int GROWTH_FACTOR = 2;
    private static final int MAX_CAPACITY = 16384;
    private static final Random RANDOM = new Random();

    // ====== 位置 (双缓冲 lerp 用) ======
    private float[] posX, posY, posZ;
    private float[] prevPosX, prevPosY, prevPosZ;

    // ====== 运动 ======
    private float[] velX, velY, velZ;
    private float[] accelX, accelY, accelZ;
    private float[] dragCoeff;

    // ====== 旋转 ======
    private float[] rot, rotRate, rotAccel;

    // ====== 外观 ======
    private float[] width, height;
    private float[] prevWidth, prevHeight; // 上一 tick 的尺寸，用于渲染插值
    private float[] r, g, b, a;
    private float[] u0, v0, u1, v1;

    // ====== 生命周期 ======
    private float[] age;
    private float[] maxLifetime;
    private boolean[] alive;

    // ====== 粒子随机种子 ======
    private float[] random1, random2, random3, random4;

    // ====== 元数据 ======
    private int count;
    private int capacity;

    public ParticleArray() {
        this(INITIAL_CAPACITY);
    }

    public ParticleArray(int initialCapacity) {
        this.capacity = Math.min(Math.max(initialCapacity, 16), MAX_CAPACITY);
        this.count = 0;

        // 位置
        posX = new float[capacity];
        posY = new float[capacity];
        posZ = new float[capacity];
        prevPosX = new float[capacity];
        prevPosY = new float[capacity];
        prevPosZ = new float[capacity];

        // 运动
        velX = new float[capacity];
        velY = new float[capacity];
        velZ = new float[capacity];
        accelX = new float[capacity];
        accelY = new float[capacity];
        accelZ = new float[capacity];
        dragCoeff = new float[capacity];

        // 旋转
        rot = new float[capacity];
        rotRate = new float[capacity];
        rotAccel = new float[capacity];

        // 外观
        width = new float[capacity];
        height = new float[capacity];
        prevWidth = new float[capacity];
        prevHeight = new float[capacity];
        r = new float[capacity];
        g = new float[capacity];
        b = new float[capacity];
        a = new float[capacity];
        u0 = new float[capacity];
        v0 = new float[capacity];
        u1 = new float[capacity];
        v1 = new float[capacity];

        // 生命周期
        age = new float[capacity];
        maxLifetime = new float[capacity];
        alive = new boolean[capacity];

        // 随机种子
        random1 = new float[capacity];
        random2 = new float[capacity];
        random3 = new float[capacity];
        random4 = new float[capacity];
    }

    // ==================== 容量管理 ====================

    private void grow() {
        int newCapacity = Math.min(capacity * GROWTH_FACTOR, MAX_CAPACITY);
        if (newCapacity <= capacity) {
            throw new IllegalStateException("粒子数组已达最大容量 " + MAX_CAPACITY);
        }

        posX = growArray(posX, newCapacity);
        posY = growArray(posY, newCapacity);
        posZ = growArray(posZ, newCapacity);
        prevPosX = growArray(prevPosX, newCapacity);
        prevPosY = growArray(prevPosY, newCapacity);
        prevPosZ = growArray(prevPosZ, newCapacity);

        velX = growArray(velX, newCapacity);
        velY = growArray(velY, newCapacity);
        velZ = growArray(velZ, newCapacity);
        accelX = growArray(accelX, newCapacity);
        accelY = growArray(accelY, newCapacity);
        accelZ = growArray(accelZ, newCapacity);
        dragCoeff = growArray(dragCoeff, newCapacity);

        rot = growArray(rot, newCapacity);
        rotRate = growArray(rotRate, newCapacity);
        rotAccel = growArray(rotAccel, newCapacity);

        width = growArray(width, newCapacity);
        height = growArray(height, newCapacity);
        prevWidth = growArray(prevWidth, newCapacity);
        prevHeight = growArray(prevHeight, newCapacity);
        r = growArray(r, newCapacity);
        g = growArray(g, newCapacity);
        b = growArray(b, newCapacity);
        a = growArray(a, newCapacity);
        u0 = growArray(u0, newCapacity);
        v0 = growArray(v0, newCapacity);
        u1 = growArray(u1, newCapacity);
        v1 = growArray(v1, newCapacity);

        age = growArray(age, newCapacity);
        maxLifetime = growArray(maxLifetime, newCapacity);
        alive = growArray(alive, newCapacity);

        random1 = growArray(random1, newCapacity);
        random2 = growArray(random2, newCapacity);
        random3 = growArray(random3, newCapacity);
        random4 = growArray(random4, newCapacity);

        this.capacity = newCapacity;
    }

    private static float[] growArray(float[] src, int newCapacity) {
        float[] dst = new float[newCapacity];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    private static boolean[] growArray(boolean[] src, int newCapacity) {
        boolean[] dst = new boolean[newCapacity];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    // ==================== 核心操作 ====================

    /**
     * 分配一个新的粒子槽位，返回其索引。
     * 所有字段初始化为默认值。
     */
    public int allocate() {
        if (count >= capacity) grow();
        int slot = count;
        count++;

        posX[slot] = 0; posY[slot] = 0; posZ[slot] = 0;
        prevPosX[slot] = 0; prevPosY[slot] = 0; prevPosZ[slot] = 0;
        velX[slot] = 0; velY[slot] = 0; velZ[slot] = 0;
        accelX[slot] = 0; accelY[slot] = 0; accelZ[slot] = 0;
        dragCoeff[slot] = 0;
        rot[slot] = 0; rotRate[slot] = 0; rotAccel[slot] = 0;
        width[slot] = 0.25f; height[slot] = 0.25f;
        prevWidth[slot] = 0.25f; prevHeight[slot] = 0.25f;
        r[slot] = 1; g[slot] = 1; b[slot] = 1; a[slot] = 1;
        u0[slot] = 0; v0[slot] = 0; u1[slot] = 1; v1[slot] = 1;
        age[slot] = 0; maxLifetime[slot] = Float.MAX_VALUE;
        alive[slot] = true;
        random1[slot] = RANDOM.nextFloat();
        random2[slot] = RANDOM.nextFloat();
        random3[slot] = RANDOM.nextFloat();
        random4[slot] = RANDOM.nextFloat();

        return slot;
    }

    /**
     * 标记指定索引的粒子为死亡（不立即回收，由 compact() 统一处理）。
     */
    public void markDead(int index) {
        if (index >= 0 && index < count) {
            alive[index] = false;
        }
    }

    /**
     * 检查指定索引的粒子是否存活。
     */
    public boolean isAlive(int index) {
        return index >= 0 && index < count && alive[index];
    }

    /**
     * 在 tick 开始时调用，保存上一 tick 的位置供渲染线程 lerp。
     */
    public void snapshotPositions() {
        System.arraycopy(posX, 0, prevPosX, 0, count);
        System.arraycopy(posY, 0, prevPosY, 0, count);
        System.arraycopy(posZ, 0, prevPosZ, 0, count);
    }

    /**
     * 在 tick 开始时调用，保存上一 tick 的尺寸供渲染线程 lerp。
     */
    public void snapshotSizes() {
        System.arraycopy(width, 0, prevWidth, 0, count);
        System.arraycopy(height, 0, prevHeight, 0, count);
    }

    /**
     * 新粒子生成后调用，将 prevPos 同步为当前 pos。
     * 避免渲染时从 (0,0,0) lerp 到实际位置。
     */
    public void initPrevPos(int index) {
        prevPosX[index] = posX[index];
        prevPosY[index] = posY[index];
        prevPosZ[index] = posZ[index];
    }

    /**
     * 新粒子生成后调用，将 prevSize 同步为当前 size。
     * 避免渲染时从默认尺寸 lerp 到实际尺寸。
     */
    public void initPrevSize(int index) {
        prevWidth[index] = width[index];
        prevHeight[index] = height[index];
    }

    /**
     * 对单个粒子执行运动积分（在主线程 tick 内调用）。
     */
    public void integrateParticle(int index, float tickDt) {
        if (!alive[index]) return;

        // 加速度积分 → 速度
        velX[index] += accelX[index] * tickDt;
        velY[index] += accelY[index] * tickDt;
        velZ[index] += accelZ[index] * tickDt;

        // 阻力: vel *= max(0, 1 - drag * dt)
        float dragFactor = Math.max(0, 1 - dragCoeff[index] * tickDt);
        velX[index] *= dragFactor;
        velY[index] *= dragFactor;
        velZ[index] *= dragFactor;

        // 速度 → 位置
        posX[index] += velX[index] * tickDt;
        posY[index] += velY[index] * tickDt;
        posZ[index] += velZ[index] * tickDt;

        // 旋转
        rotRate[index] += rotAccel[index] * tickDt;
        rot[index] += rotRate[index] * tickDt;

        // 年龄
        age[index] += tickDt;
    }

    /**
     * 全量运动积分。遍历所有存活粒子执行 integrateParticle。
     */
    public void integrate(float tickDt) {
        for (int i = 0; i < count; i++) {
            integrateParticle(i, tickDt);
        }
    }

    /**
     * 批量紧凑：将所有存活粒子移动到数组前部，更新 count。
     * 仅在所有粒子遍历完成后、swap 之前调用一次。
     */
    public void compact() {
        int writeIdx = 0;
        for (int readIdx = 0; readIdx < count; readIdx++) {
            if (!alive[readIdx]) continue;
            if (writeIdx != readIdx) copySlotTo(readIdx, writeIdx);
            writeIdx++;
        }
        count = writeIdx;
    }

    private void copySlotTo(int src, int dst) {
        posX[dst] = posX[src];      posY[dst] = posY[src];      posZ[dst] = posZ[src];
        prevPosX[dst] = prevPosX[src]; prevPosY[dst] = prevPosY[src]; prevPosZ[dst] = prevPosZ[src];
        velX[dst] = velX[src];      velY[dst] = velY[src];      velZ[dst] = velZ[src];
        accelX[dst] = accelX[src];  accelY[dst] = accelY[src];  accelZ[dst] = accelZ[src];
        dragCoeff[dst] = dragCoeff[src];
        rot[dst] = rot[src];        rotRate[dst] = rotRate[src]; rotAccel[dst] = rotAccel[src];
        width[dst] = width[src];    height[dst] = height[src];
        prevWidth[dst] = prevWidth[src]; prevHeight[dst] = prevHeight[src];
        r[dst] = r[src]; g[dst] = g[src]; b[dst] = b[src]; a[dst] = a[src];
        u0[dst] = u0[src]; v0[dst] = v0[src]; u1[dst] = u1[src]; v1[dst] = v1[src];
        age[dst] = age[src];        maxLifetime[dst] = maxLifetime[src];
        alive[dst] = alive[src];
        random1[dst] = random1[src]; random2[dst] = random2[src];
        random3[dst] = random3[src]; random4[dst] = random4[src];
    }

    /**
     * 清空所有粒子。
     */
    public void clear() {
        count = 0;
    }

    // ==================== 渲染线程 lerp 访问 ====================

    /** 渲染线程调用 — 纯读，无副作用 */
    public float getRenderX(int index, float partialTick) {
        return prevPosX[index] + (posX[index] - prevPosX[index]) * partialTick;
    }

    /** 渲染线程调用 — 纯读，无副作用 */
    public float getRenderY(int index, float partialTick) {
        return prevPosY[index] + (posY[index] - prevPosY[index]) * partialTick;
    }

    /** 渲染线程调用 — 纯读，无副作用 */
    public float getRenderZ(int index, float partialTick) {
        return prevPosZ[index] + (posZ[index] - prevPosZ[index]) * partialTick;
    }

    /** 渲染线程调用 — 对 width 做 partialTick lerp */
    public float getRenderWidth(int index, float partialTick) {
        return prevWidth[index] + (width[index] - prevWidth[index]) * partialTick;
    }

    /** 渲染线程调用 — 对 height 做 partialTick lerp */
    public float getRenderHeight(int index, float partialTick) {
        return prevHeight[index] + (height[index] - prevHeight[index]) * partialTick;
    }

    // ==================== Getter/Setter ====================

    public int getCount() { return count; }
    public int getCapacity() { return capacity; }

    // 位置
    public float getPosX(int i) { return posX[i]; }
    public float getPosY(int i) { return posY[i]; }
    public float getPosZ(int i) { return posZ[i]; }
    public void setPos(int i, float x, float y, float z) { posX[i] = x; posY[i] = y; posZ[i] = z; }

    public float getPrevPosX(int i) { return prevPosX[i]; }
    public float getPrevPosY(int i) { return prevPosY[i]; }
    public float getPrevPosZ(int i) { return prevPosZ[i]; }

    // 速度
    public float getVelX(int i) { return velX[i]; }
    public float getVelY(int i) { return velY[i]; }
    public float getVelZ(int i) { return velZ[i]; }
    public void setVel(int i, float x, float y, float z) { velX[i] = x; velY[i] = y; velZ[i] = z; }

    // 加速度
    public float getAccelX(int i) { return accelX[i]; }
    public float getAccelY(int i) { return accelY[i]; }
    public float getAccelZ(int i) { return accelZ[i]; }
    public void setAccel(int i, float x, float y, float z) { accelX[i] = x; accelY[i] = y; accelZ[i] = z; }
    public void setAccelX(int i, float v) { accelX[i] = v; }
    public void setAccelY(int i, float v) { accelY[i] = v; }
    public void setAccelZ(int i, float v) { accelZ[i] = v; }

    // 阻力
    public float getDragCoeff(int i) { return dragCoeff[i]; }
    public void setDragCoeff(int i, float v) { dragCoeff[i] = v; }

    // 旋转
    public float getRot(int i) { return rot[i]; }
    public void setRot(int i, float v) { rot[i] = v; }
    public float getRotRate(int i) { return rotRate[i]; }
    public void setRotRate(int i, float v) { rotRate[i] = v; }
    public float getRotAccel(int i) { return rotAccel[i]; }
    public void setRotAccel(int i, float v) { rotAccel[i] = v; }

    // 外观
    public float getWidth(int i) { return width[i]; }
    public void setWidth(int i, float v) { width[i] = v; }
    public float getHeight(int i) { return height[i]; }
    public void setHeight(int i, float v) { height[i] = v; }

    public float getR(int i) { return r[i]; }
    public void setR(int i, float v) { r[i] = v; }
    public float getG(int i) { return g[i]; }
    public void setG(int i, float v) { g[i] = v; }
    public float getB(int i) { return b[i]; }
    public void setB(int i, float v) { b[i] = v; }
    public float getA(int i) { return a[i]; }
    public void setA(int i, float v) { a[i] = v; }
    public void setColor(int i, float r, float g, float b, float a) {
        this.r[i] = r; this.g[i] = g; this.b[i] = b; this.a[i] = a;
    }

    public float getU0(int i) { return u0[i]; }
    public void setU0(int i, float v) { u0[i] = v; }
    public float getV0(int i) { return v0[i]; }
    public void setV0(int i, float v) { v0[i] = v; }
    public float getU1(int i) { return u1[i]; }
    public void setU1(int i, float v) { u1[i] = v; }
    public float getV1(int i) { return v1[i]; }
    public void setV1(int i, float v) { v1[i] = v; }
    public void setUV(int i, float u0, float v0, float u1, float v1) {
        this.u0[i] = u0; this.v0[i] = v0; this.u1[i] = u1; this.v1[i] = v1;
    }

    // 生命周期
    public float getAge(int i) { return age[i]; }
    public void setAge(int i, float v) { age[i] = v; }
    public float getMaxLifetime(int i) { return maxLifetime[i]; }
    public void setMaxLifetime(int i, float v) { maxLifetime[i] = v; }

    // 随机种子
    public float getRandom1(int i) { return random1[i]; }
    public float getRandom2(int i) { return random2[i]; }
    public float getRandom3(int i) { return random3[i]; }
    public float getRandom4(int i) { return random4[i]; }
}
