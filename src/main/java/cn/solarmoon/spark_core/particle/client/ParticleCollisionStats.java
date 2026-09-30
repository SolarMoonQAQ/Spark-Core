package cn.solarmoon.spark_core.particle.client;

/**
 * 粒子碰撞统计（每发射器实例一份）。
 * <p>
 * 计数器**不放在静态字段**：热重载与多发射器实例都会让它失真。计数全部由
 * {@link CollisionResolver#move} 返回的位标志累加，因此 {@code CollisionResolver} 本身无需感知统计。
 * <p>
 * {@code penetrationTicks} 是唯一需要额外方块查询的指标（每粒子每 tick 一次
 * {@code getBlockState}），默认关闭，仅在验证时通过
 * {@code /spark particle collision-stats penetration} 开启。
 */
public class ParticleCollisionStats {

    /** penetrationTicks 采样开关（全局，仅验证用；关闭时不产生任何额外开销） */
    private static volatile boolean penetrationSampling = false;

    // ====== 本 tick 计数（每次 updateParticles 开头清零）======
    /** 本 tick 发生的碰撞次数 */
    public long collisionsThisTick;
    /** 本 tick 的碰撞次数峰值 */
    public long maxCollisionsPerTick;

    // ====== 累计计数 ======
    /** 累计碰撞次数（确认扫掠确实在跑） */
    public long collisions;
    /** 接触上升沿触发的碰撞事件数（粒子静止期间不应继续增长） */
    public long contactEdges;
    /** expire_on_contact 致死次数 */
    public long deadByContact;
    /** 粒子中心点位于非空气方块内且未致死的 tick 数（目标趋近 0） */
    public long penetrationTicks;

    public static boolean isPenetrationSamplingEnabled() { return penetrationSampling; }

    public static void setPenetrationSamplingEnabled(boolean enabled) { penetrationSampling = enabled; }

    /** 每 tick 遍历粒子前调用，清零本 tick 计数并更新峰值。 */
    public void beginTick() {
        if (collisionsThisTick > maxCollisionsPerTick) maxCollisionsPerTick = collisionsThisTick;
        collisionsThisTick = 0;
    }

    public void reset() {
        collisionsThisTick = 0;
        maxCollisionsPerTick = 0;
        collisions = 0;
        contactEdges = 0;
        deadByContact = 0;
        penetrationTicks = 0;
    }

    /** 累加另一个实例的计数（供指令聚合）。 */
    public void addFrom(ParticleCollisionStats other) {
        collisions += other.collisions;
        contactEdges += other.contactEdges;
        deadByContact += other.deadByContact;
        penetrationTicks += other.penetrationTicks;
        if (other.maxCollisionsPerTick > maxCollisionsPerTick) maxCollisionsPerTick = other.maxCollisionsPerTick;
        collisionsThisTick += other.collisionsThisTick;
    }
}
