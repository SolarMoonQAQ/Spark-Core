package cn.solarmoon.spark_core.particle.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 粒子位移与方块碰撞扫掠。取代 {@code ParticleArray.integrateParticle()} 成为唯一的位置推进点。
 * <p>
 * 所有粒子速度单位为 方块/秒，tickDt 固定 0.05（ParticleVisualEffectRenderer）。
 * 仅在 (collFlags & COLL_ENABLED) != 0 且 Level 可用时执行扫掠，其余粒子走无碰撞快路径
 * （与旧 {@code integrateParticle} 逐位等价）。
 * <p>
 * 语义基线：Bedrock particle_effect v1.10.0 + 原版 {@code Particle.move} /
 * {@code Entity.collideBoundingBox}。轴顺序、裁剪判定与速度上限保护均由原版内核负责，本类不自行实现。
 */
public final class CollisionResolver {

    private CollisionResolver() {}

    /** 原版 Particle 包围盒半边长（0.2 立方）；本计划以粒子坐标为中心而非底部锚定 */
    public static final float BASE_HALF = 0.1f;

    /** collision_radius 的规范上限：基岩要求 ≤ 1/2 格 */
    public static final float MAX_COLLISION_RADIUS = 0.5f;

    /**
     * 与原版 {@code Particle.MAXIMUM_COLLISION_VELOCITY_SQUARED} 同源的上限保护：
     * 待位移平方超过此值则不做扫掠，避免 expandTowards 产生超大 AABB。
     */
    public static final double MAX_MOVE_SQR = 10000.0;

    /**
     * 裁剪判定阈值：原版 {@code Shapes.collide} 对 |desiredOffset| < 1.0E-7 的分量直接返回 0，
     * 因此「返回值与输入不等」并不等价于「撞到了方块」，不能用浮点全等比较（会产生幻影碰撞）。
     */
    public static final double CLIP_EPSILON = 1.0E-7;

    /** 本 tick 发生轴向裁剪 */
    public static final byte RESULT_COLLIDED = 1;
    /** 因 expire_on_contact 致死（调用方需触发过期事件） */
    public static final byte RESULT_EXPIRED = 1 << 1;
    /** 碰撞发生在接触位的上升沿（上一 tick 未贴住方块）→ 碰撞事件应触发 */
    public static final byte RESULT_CONTACT_EDGE = 1 << 2;

    /**
     * 单粒子运动积分 + 方块扫掠 + 逐轴反弹。
     *
     * @param impact 调用方复用的长度 ≥ 1 暂存器；发生碰撞时写入反弹前的 |v|（方块/秒），供 min_speed 过滤
     * @param level  方块碰撞查询所需的 Level；为 null 时只做无碰撞积分（第一人称实例门控即走此路径）
     * @return 位标志组合：{@link #RESULT_COLLIDED} / {@link #RESULT_EXPIRED} / {@link #RESULT_CONTACT_EDGE}
     */
    public static byte move(ParticleArray buf, int i, float tickDt, @Nullable Level level, float[] impact) {
        if (!buf.isAlive(i)) return 0;

        // ── 1. 速度积分（公式与旧 ParticleArray.integrateParticle 逐位等价）──
        float vx = buf.getVelX(i) + buf.getAccelX(i) * tickDt;
        float vy = buf.getVelY(i) + buf.getAccelY(i) * tickDt;
        float vz = buf.getVelZ(i) + buf.getAccelZ(i) * tickDt;
        float dragFactor = Math.max(0f, 1f - buf.getDragCoeff(i) * tickDt);
        vx *= dragFactor;
        vy *= dragFactor;
        vz *= dragFactor;

        // ── 2. 待位移（保持 float 运算：无碰撞路径因此与旧实现逐位等价）──
        float fx = vx * tickDt;
        float fy = vy * tickDt;
        float fz = vz * tickDt;

        // ── 3. 方块扫掠 ──
        // 零位移不扫掠（原版 Particle.move 同样以 x/y/z 全零短路），否则静止粒子每 tick 白付一次分配
        byte result = 0;
        byte flags = buf.getCollFlags(i);   // 组件本 tick 已读-改-写，接触位可用
        if (level != null && (flags & ParticleArray.COLL_ENABLED) != 0
                && (fx != 0f || fy != 0f || fz != 0f)
                && (double) fx * fx + (double) fy * fy + (double) fz * fz < MAX_MOVE_SQR) {

            float half = halfExtent(buf.getCollRadius(i));
            double dx = fx;
            double dy = fy;
            double dz = fz;
            float px = buf.getPosX(i);
            float py = buf.getPosY(i);
            float pz = buf.getPosZ(i);
            AABB box = new AABB(px - half, py - half, pz - half,
                                px + half, py + half, pz + half);

            // 原版内核：逐轴裁剪位移（Y → 位移较大的水平轴 → 另一轴），返回不穿透的位移
            Vec3 clipped = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), box, level, List.of());

            boolean hitX = Math.abs(clipped.x - dx) > CLIP_EPSILON;
            boolean hitY = Math.abs(clipped.y - dy) > CLIP_EPSILON;
            boolean hitZ = Math.abs(clipped.z - dz) > CLIP_EPSILON;

            if (hitX || hitY || hitZ) {
                result |= RESULT_COLLIDED;
                impact[0] = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);   // 反弹前速度
                float restitution = buf.getCollRestitution(i);
                float cDrag = buf.getCollDrag(i);
                if (hitX) vx = bounce(vx, cDrag, restitution);
                if (hitY) vy = bounce(vy, cDrag, restitution);
                if (hitZ) vz = bounce(vz, cDrag, restitution);
                // 未被裁剪的轴 clipped 与输入逐位相同，故回写 float 不改变快路径结果
                fx = (float) clipped.x;
                fy = (float) clipped.y;
                fz = (float) clipped.z;
            }
        }

        // ── 4. 写回位置与速度 ──
        buf.setVel(i, vx, vy, vz);
        buf.setPos(i, buf.getPosX(i) + fx,
                      buf.getPosY(i) + fy,
                      buf.getPosZ(i) + fz);

        // ── 5. 旋转与年龄（公式同旧 integrateParticle）──
        float rotRate = buf.getRotRate(i) + buf.getRotAccel(i) * tickDt;
        buf.setRotRate(i, rotRate);
        buf.setRot(i, buf.getRot(i) + rotRate * tickDt);
        buf.setAge(i, buf.getAge(i) + tickDt);

        // ── 6. 接触位（仅用于碰撞事件去重；不短路扫掠）──
        if ((result & RESULT_COLLIDED) != 0) {
            if ((flags & ParticleArray.COLL_CONTACT) == 0) result |= RESULT_CONTACT_EDGE;
            flags |= ParticleArray.COLL_CONTACT;
        } else {
            flags &= ~ParticleArray.COLL_CONTACT;
        }
        // 未声明碰撞组件的粒子 collFlags 恒 0，此处连一次字节写都不产生（§4.3 的零额外开销）
        if (flags != 0) buf.setCollFlags(i, flags);

        // ── 7. expire_on_contact ──
        if ((result & RESULT_COLLIDED) != 0 && (flags & ParticleArray.COLL_EXPIRE) != 0) {
            buf.markDead(i);
            result |= RESULT_EXPIRED;
        }
        return result;
    }

    /**
     * 碰撞盒半边长（方块）。半径按规范夹取到 [0, {@link #MAX_COLLISION_RADIUS}]：
     * 不夹取时一个 {@code collision_radius: 5} 的笔误就能让 expandTowards 造出超大 AABB。
     */
    public static float halfExtent(float radius) {
        if (!(radius > 0f)) return BASE_HALF;                       // 同时覆盖 NaN 与负值
        return BASE_HALF + Math.min(radius, MAX_COLLISION_RADIUS);
    }

    /**
     * 逐轴碰撞响应。v 与 drag 单位均为 方块/秒。
     * 对速度下限做 0 截断：drag 大于速度时不反向弹射。
     */
    static float bounce(float v, float drag, float restitution) {
        float magnitude = Math.max(0f, Math.abs(v) - drag);
        return -Mth.sign(v) * magnitude * restitution;
    }
}
