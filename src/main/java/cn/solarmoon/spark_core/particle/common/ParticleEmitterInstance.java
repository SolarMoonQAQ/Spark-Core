package cn.solarmoon.spark_core.particle.common;

import cn.solarmoon.spark_core.particle.client.*;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import cn.solarmoon.spark_core.particle.common.data.IComponentDefinition;
import cn.solarmoon.spark_core.particle.common.data.IEmitterComponent;
import cn.solarmoon.spark_core.particle.common.data.IParticleComponent;
import cn.solarmoon.spark_core.particle.common.data.EmitterPreset;
import cn.solarmoon.spark_core.particle.common.data.ParticlePreset;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.EmitterLifetimeEvents;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.EmitterLifetimeLooping;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.ParticleLifetimeEvents;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 粒子发射器实例。管理单个粒子效果的完整生命周期。
 * <p>
 * tick 流程:
 * ① startTick() → ② snapshotPositions() → ③ Emitter 寿命/速率
 * → ④ 发射新粒子 (形状 + 组件初始化) → ⑤ 对每个粒子: Molang → 运动积分 → 碰撞 → expiration
 * → ⑥ compact() → ⑦ swap()
 */
public class ParticleEmitterInstance {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final UUID instanceId = UUID.randomUUID();
    private final int localId = ID_COUNTER.incrementAndGet();

    private final ParticleEffectDefinition definition;
    private final ParticleDoubleBuffer doubleBuffer;
    private final ParticleMolangEnvironment molang;
    private final CurveEvaluator curveEvaluator;
    private final EventExecutor eventExecutor;

    // 发射器状态
    private float emitterAge = 0;
    private float emitterLifetime = 0;
    private boolean active = true;
    private boolean expired = false;
    private final Matrix4f transform = new Matrix4f();
    private boolean bindToActor = false;

    /** 运行时贴图，构造时默认取 JSON 定义中的贴图，外部可随时修改实现动态换贴图 */
    private ResourceLocation texture;

    // 发射器发射速率累加器
    private float spawnAccumulator = 0;
    private boolean instantFired = false; // EmitterRateInstant 是否已触发
    private Level level;

    // EmitterLifetimeLooping 相位追踪（用于睡眠/激活循环 + 周期重置）
    private final boolean hasLooping;
    private boolean wasInSleepPhase = false;

    // 运行时组件
    private final IEmitterComponent[] emitterComponents;
    private final IParticleComponent[] particleComponents;

    // 暂存待删除粒子索引（使用 IntArrayList 避免高粒子数下的 Integer 装箱 GC 压力）
    private final IntArrayList pendingDead = new IntArrayList();

    // 发射器随机数
    private final double[] emitterRandoms = new double[4];
    private final Random random = new Random();

    // 发射器速度（方块/秒），用于 emitter_local_space.velocity 继承
    private Vec3 velocity = Vec3.ZERO;

    // 粒子组件列表（含 onApply 组件）
    private final List<IParticleComponent> allParticleComponents;

    // ====== 事件系统追踪状态 ======
    /** 每个粒子的时间线触发索引，key=粒子在 buf 中的 index，value=已触发到的 timeline 条目索引 */
    private final Int2IntOpenHashMap particleTimelineTracker = new Int2IntOpenHashMap();
    /** 发射器时间线已触发到的条目索引 */
    private int emitterTimelineLastIndex = 0;

    public ParticleEmitterInstance(ParticleEffectDefinition definition) {
        this(definition, null);
    }

    public ParticleEmitterInstance(ParticleEffectDefinition definition, @Nullable Level level) {
        this.definition = definition;
        this.level = level;
        this.doubleBuffer = new ParticleDoubleBuffer(256);
        this.molang = new ParticleMolangEnvironment();
        this.curveEvaluator = new CurveEvaluator(definition.getCurves(), molang);
        this.eventExecutor = new EventExecutor(molang, this, level);
        this.texture = definition.getDescription().getTexture(); // 默认取 JSON 定义的贴图

        // 创建运行时组件
        EmitterPreset emitterPreset = definition.getEmitterPreset();
        ParticlePreset particlePreset = definition.getParticlePreset();
        this.emitterComponents = emitterPreset.getAllComponents();
        this.particleComponents = particlePreset.getAllComponents();

        // 收集需要 onApply 的粒子组件
        this.allParticleComponents = Arrays.asList(particleComponents);

        // 检测是否存在 EmitterLifetimeLooping（用于相位追踪和循环重置）
        boolean foundLooping = false;
        for (IComponentDefinition def : definition.getEmitterPreset().getDefinitions()) {
            if (def instanceof EmitterLifetimeLooping) {
                foundLooping = true;
                break;
            }
        }
        this.hasLooping = foundLooping;

        // 初始化发射器随机数
        for (int i = 0; i < 4; i++) {
            emitterRandoms[i] = random.nextDouble();
        }

        // 重新生成随机数，与粒子随机数不同
        resetEmitterRandoms();
    }

    private void resetEmitterRandoms() {
        for (int i = 0; i < 4; i++) {
            emitterRandoms[i] = random.nextDouble();
        }
    }

    /**
     * 主 tick 循环（由主线程调用）。
     */
    public void tick(Level level, float tickDt) {
        if (expired) return;

        ParticleArray buf = doubleBuffer.startTick();

        // 1. 保存上一 tick 位置和尺寸
        buf.snapshotPositions();
        buf.snapshotSizes();

        // 1b. 设置 Molang 环境的 Level 引用（供需要碰撞检测的组件使用）
        molang.setLevel(level);

        // 2. 绑定发射器 Molang 变量
        molang.bindEmitter(emitterAge, emitterLifetime, emitterRandoms);

        // 3. 曲线求值（发射器级）
        curveEvaluator.evaluateEmitter();

        // 4. Emitter 组件 tick（传入发射器自身的 Molang 环境）
        for (IEmitterComponent comp : emitterComponents) {
            comp.tick(buf, tickDt, molang);
        }

        // 5. 粒子发射逻辑
        emitParticles(buf, tickDt);

        // 6. 每个粒子更新 + 运动积分 + 过期检查
        updateParticles(buf, tickDt, level);

        // 7. 统一标记死亡
        for (int idx : pendingDead) {
            buf.markDead(idx);
        }
        pendingDead.clear();

        // 8. compact
        buf.compact();

        // 9. swap
        doubleBuffer.swap();

        // 10. 触发发射器时间线事件
        fireEmitterTimeline();

        // 11. 更新发射器年龄
        emitterAge += tickDt;

        // 12. 检查发射器过期
        checkEmitterLifetime();
    }

    /**
     * 发射新粒子。
     * <p>
     * World 模式（默认）：粒子存世界坐标，onSpawn 计算的相对偏移上叠加 emitter 位置。
     * Local 模式（hasLocalPosition=true）：粒子存相对坐标，渲染时由 PoseStack 偏移。
     */
    private void emitParticles(ParticleArray buf, float tickDt) {
        int spawnCount = getSpawnCount(tickDt);
        // max_particles 限制的是同时存活的粒子数（不是累计）
        spawnCount = Math.min(spawnCount, getMaxParticles() - buf.getCount());

        boolean isLocal = definition.getEmitterPreset().hasLocalPosition();

        for (int s = 0; s < spawnCount; s++) {
            int idx = buf.allocate();

            // 立即绑定新粒子的 Molang 变量（age=0, lifetime=1.0 为默认占位值，
            // 后续 setMaxLifetime 和 onApply 会更新，此处主要是让随机数等变量可用）
            molang.bindParticle(0, 1.0f, new double[]{
                    buf.getRandom1(idx), buf.getRandom2(idx),
                    buf.getRandom3(idx), buf.getRandom4(idx)
            });

            // 调用所有发射器组件的 onSpawn（形状设置位置/速度+方向，初始化设置变量等）
            for (IEmitterComponent comp : emitterComponents) {
                comp.onSpawn(buf, idx, molang);
            }

            // 初始化粒子生命周期默认值
            buf.setAge(idx, 0);
            buf.setMaxLifetime(idx, 1.0f);

            // 粒子组件 onApply（尺寸/UV/颜色/初始速度/自旋等）
            for (IParticleComponent comp : allParticleComponents) {
                comp.onApply(buf, idx, molang);
            }

            // World 模式：用完整变换矩阵将局部偏移转为世界坐标（含旋转和缩放）
            // Local 模式：粒子存相对坐标，不做偏移
            if (!isLocal) {
                float ox = buf.getPosX(idx);
                float oy = buf.getPosY(idx);
                float oz = buf.getPosZ(idx);
                Vector4f worldPos = transform.transform(new Vector4f(ox, oy, oz, 1.0f));
                buf.setPos(idx, worldPos.x, worldPos.y, worldPos.z);

                // 速度方向也需要旋转到世界空间（w=0 跳过平移，仅应用旋转和缩放）
                float vx = buf.getVelX(idx);
                float vy = buf.getVelY(idx);
                float vz = buf.getVelZ(idx);
                Vector4f worldVel = transform.transform(new Vector4f(vx, vy, vz, 0.0f));
                buf.setVel(idx, worldVel.x, worldVel.y, worldVel.z);
            }

            // emitter_local_space.velocity：将发射器速度叠加到粒子初始速度
            if (definition.getEmitterPreset().hasLocalVelocity() && !velocity.equals(Vec3.ZERO)) {
                buf.setVel(idx,
                        buf.getVelX(idx) + (float) velocity.x,
                        buf.getVelY(idx) + (float) velocity.y,
                        buf.getVelZ(idx) + (float) velocity.z);
            }

            // 新粒子立即同步 prevPos/prevSize = 当前值，避免渲染时从默认值 lerp 导致瞬移
            buf.initPrevPos(idx);
            buf.initPrevSize(idx);

            // 触发粒子创建事件
            ParticleLifetimeEvents particleEvents = definition.getParticlePreset().findLifetimeEvents();
            if (particleEvents != null && !particleEvents.getCreationEvent().isEmpty()) {
                // creationEvent 可以是单个事件名或逗号分隔的多个名称
                String[] names = particleEvents.getCreationEvent().split(",");
                for (String name : names) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        // 查找 events map 中对应的事件列表并执行
                        List<EventNode> creationNodes = definition.getEvents() != null
                                ? definition.getEvents().get(trimmed) : null;
                        if (creationNodes != null) {
                            eventExecutor.execute(creationNodes, buf, idx);
                        }
                    }
                }
            }

        }
    }

    /**
     * 计算本 tick 应发射的粒子数。
     * <p>
     * 各速率组件的策略:
     * - EmitterRateInstant: 仅首次 tick 发射 numParticles 个
     * - EmitterRateSteady: 基于 spawnRate 累加累积，每次取整
     * - EmitterRateManual: 不自发发射（由外部 trigger 控制）
     */
    private int getSpawnCount(float tickDt) {
        // EmitterLifetimeLooping 睡眠阶段不发射粒子
        if (hasLooping && !isInActivePhase()) {
            return 0;
        }

        EmitterPreset preset = definition.getEmitterPreset();

        // EmitterRateInstant：仅首次 tick 发射
        var numExpr = preset.getNumParticlesExpr();
        if (numExpr != null) {
            if (!instantFired) {
                instantFired = true;
                float num = (float) molang.evaluate(numExpr);
                return Math.max(1, Math.round(num));
            }
            return 0;
        }

        // EmitterRateSteady：基于速率累加
        var rateExpr = preset.getSpawnRateExpr();
        if (rateExpr != null) {
            float rate = (float) molang.evaluate(rateExpr);
            spawnAccumulator += rate * tickDt;
            int count = (int) spawnAccumulator;
            spawnAccumulator -= count;
            return Math.max(0, count);
        }

        return 0;
    }

    /**
     * 获取最大粒子数上限。
     */
    private int getMaxParticles() {
        EmitterPreset preset = definition.getEmitterPreset();

        var maxExpr = preset.getMaxParticlesExpr();
        if (maxExpr != null) {
            float val = (float) molang.evaluate(maxExpr);
            return Math.max(1, Math.round(val));
        }

        // 无 maxParticles 组件时回退：若为 Instant 则用其 numParticles 作为上限
        var numExpr = preset.getNumParticlesExpr();
        if (numExpr != null) {
            float num = (float) molang.evaluate(numExpr);
            return Math.max(1, Math.round(num));
        }

        return 16384;
    }

    private void updateParticles(ParticleArray buf, float tickDt, Level level) {
        int n = buf.getCount();

        for (int i = 0; i < n; i++) {
            if (!buf.isAlive(i)) continue;

            // 过期检查
            if (buf.getAge(i) >= buf.getMaxLifetime(i)) {
                eventExecutor.fireExpirationEvents(i, buf);
                pendingDead.add(i);
                continue;
            }

            // 绑定粒子 Molang 变量
            float age = buf.getAge(i);
            float maxLife = buf.getMaxLifetime(i);
            double[] particleRandoms = new double[]{
                    buf.getRandom1(i), buf.getRandom2(i),
                    buf.getRandom3(i), buf.getRandom4(i)
            };
            molang.bindParticle(age, maxLife, particleRandoms);

            // 曲线求值（粒子级）
            curveEvaluator.evaluate(buf, i);

            // 粒子组件 tick（传入发射器自身的 Molang 环境，确保 particle_age 等变量正确）
            for (IParticleComponent comp : particleComponents) {
                comp.tick(buf, i, tickDt, molang);
            }

            // 触发粒子时间线事件
            ParticleLifetimeEvents particleEvents = definition.getParticlePreset().findLifetimeEvents();
            if (particleEvents != null && !particleEvents.getTimeline().isEmpty()) {
                float particleAge = buf.getAge(i);
                int lastIdx = particleTimelineTracker.getOrDefault(i, -1);
                int newLastIdx = fireParticleTimeline(particleEvents, particleAge, lastIdx, buf, i);
                if (newLastIdx != lastIdx) {
                    particleTimelineTracker.put(i, newLastIdx);
                }
            }

            // 运动积分
            buf.integrateParticle(i, tickDt);
        }

        // 清理已过期粒子的 timeline 追踪记录
        particleTimelineTracker.int2IntEntrySet().removeIf(entry -> !buf.isAlive(entry.getIntKey()));
    }

    /**
     * 检查发射器是否应标记为过期。
     * <p>
     * EmitterLifetimeLooping 永不过期，但会在 sleep→active 切换时重置周期状态
     * （instantFired），使下一轮循环可以重新发射粒子。
     */
    private void checkEmitterLifetime() {
        // 检查相位切换并做周期重置
        if (hasLooping) {
            boolean currentlySleeping = !isInActivePhase();
            // sleep → active 切换：重置周期状态，准备新一轮发射
            if (wasInSleepPhase && !currentlySleeping) {
                instantFired = false;
            }
            wasInSleepPhase = currentlySleeping;
            return; // Looping 永不过期
        }

        EmitterPreset preset = definition.getEmitterPreset();

        var activeTimeExpr = preset.getActiveTimeExpr();
        if (activeTimeExpr != null) {
            float activeTime = (float) molang.evaluate(activeTimeExpr);
            if (emitterAge >= activeTime) {
                // 触发发射器过期事件
                EmitterLifetimeEvents emitterEvents = preset.findLifetimeEvents();
                if (emitterEvents != null && !emitterEvents.getExpirationEvent().isEmpty()) {
                    String[] names = emitterEvents.getExpirationEvent().split(",");
                    for (String name : names) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            List<EventNode> nodes = definition.getEvents() != null
                                    ? definition.getEvents().get(trimmed) : null;
                            if (nodes != null) eventExecutor.execute(nodes);
                        }
                    }
                }
                expired = true;
            }
            return;
        }

        var expirationExpr = preset.getExpirationExpr();
        if (expirationExpr != null) {
            double val = molang.evaluate(expirationExpr);
            if (val > 0) {
                // 触发发射器过期事件
                EmitterLifetimeEvents emitterEvents = preset.findLifetimeEvents();
                if (emitterEvents != null && !emitterEvents.getExpirationEvent().isEmpty()) {
                    String[] names = emitterEvents.getExpirationEvent().split(",");
                    for (String name : names) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            List<EventNode> nodes = definition.getEvents() != null
                                    ? definition.getEvents().get(trimmed) : null;
                            if (nodes != null) eventExecutor.execute(nodes);
                        }
                    }
                }
                expired = true;
            }
        }
    }

    /**
     * 查询运行时组件中是否有任意一个处于非激活相位（即睡眠中）。
     */
    private boolean isInActivePhase() {
        for (IEmitterComponent comp : emitterComponents) {
            if (!comp.isInActivePhase()) return false;
        }
        return true;
    }

    // ====== Getters/Setters ======

    public UUID getInstanceId() { return instanceId; }
    public ParticleEffectDefinition getDefinition() { return definition; }
    public ParticleDoubleBuffer getDoubleBuffer() { return doubleBuffer; }
    public boolean isActive() { return active && !expired; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }
    public float getEmitterAge() { return emitterAge; }

    // ====== 事件系统辅助方法 ======

    /**
     * 触发发射器时间线事件（在 tick 末尾调用）。
     */
    private void fireEmitterTimeline() {
        EmitterLifetimeEvents emitterEvents = definition.getEmitterPreset().findLifetimeEvents();
        if (emitterEvents == null) return;

        // 触发 timeline 事件
        if (!emitterEvents.getTimeline().isEmpty()) {
            int idx = 0;
            for (Map.Entry<Float, String> entry : emitterEvents.getTimeline().entrySet()) {
                if (idx < emitterTimelineLastIndex) { idx++; continue; }
                if (emitterAge >= entry.getKey()) {
                    String[] names = entry.getValue().split(",");
                    for (String name : names) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            List<EventNode> nodes = definition.getEvents() != null
                                    ? definition.getEvents().get(trimmed) : null;
                            if (nodes != null) {
                                eventExecutor.execute(nodes);
                            }
                        }
                    }
                    emitterTimelineLastIndex = idx + 1;
                }
                idx++;
            }
        }

        // 触发 travel_distance_events
        if (!emitterEvents.getTravelDistanceEvents().isEmpty()) {
            // travel distance events 需要追踪发射器移动距离，暂仅处理 timeline
        }

        // 触发 looping_travel_distance_events
        if (!emitterEvents.getLoopingTravelDistanceEvents().isEmpty()) {
            // 暂未实现循环移动距离事件
        }
    }

    /**
     * 触发粒子时间线事件。
     *
     * @param particleEvents 粒子生命周期事件定义
     * @param particleAge    粒子当前年龄
     * @param lastIndex      上次触发到的索引
     * @param buf            粒子数组
     * @param particleIndex  粒子索引
     * @return 更新后的 lastIndex
     */
    private int fireParticleTimeline(ParticleLifetimeEvents particleEvents, float particleAge,
                                     int lastIndex, ParticleArray buf, int particleIndex) {
        if (particleEvents.getTimeline().isEmpty()) return lastIndex;
        int idx = 0;
        for (Map.Entry<Float, String> entry : particleEvents.getTimeline().entrySet()) {
            if (idx <= lastIndex) { idx++; continue; }
            if (particleAge >= entry.getKey()) {
                String[] names = entry.getValue().split(",");
                for (String name : names) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        List<EventNode> nodes = definition.getEvents() != null
                                ? definition.getEvents().get(trimmed) : null;
                        if (nodes != null) {
                            eventExecutor.execute(nodes, buf, particleIndex);
                        }
                    }
                }
                lastIndex = idx;
            }
            idx++;
        }
        return lastIndex;
    }

    /** 获取发射器位置（从变换矩阵提取平移分量）。 */
    public Vec3 getPosition() {
        return new Vec3(transform.m30(), transform.m31(), transform.m32());
    }

    /** 设置发射器位置（仅平移，保持旋转和缩放不变）。 */
    public void setPosition(Vec3 pos) {
        transform.m30((float) pos.x);
        transform.m31((float) pos.y);
        transform.m32((float) pos.z);
    }

    /** 获取发射器旋转（从变换矩阵分解出标准化四元数）。 */
    public Quaternionf getRotation() {
        Quaternionf q = new Quaternionf();
        transform.getNormalizedRotation(q);
        return q;
    }

    /** 设置发射器旋转（保持位置和缩放不变）。 */
    public void setRotation(Quaternionf quat) {
        Vec3 pos = getPosition();
        Vec3 scale = getScale();
        transform.identity();
        transform.translate((float) pos.x, (float) pos.y, (float) pos.z);
        transform.rotate(quat);
        transform.scale((float) scale.x, (float) scale.y, (float) scale.z);
    }

    /** 获取发射器缩放（从变换矩阵分解出各轴缩放）。 */
    public Vec3 getScale() {
        org.joml.Vector3f s = new org.joml.Vector3f();
        transform.getScale(s);
        return new Vec3(s.x(), s.y(), s.z());
    }

    /** 设置发射器缩放（非均匀，保持位置和旋转不变）。 */
    public void setScale(Vec3 scale) {
        Vec3 pos = getPosition();
        Quaternionf quat = getRotation();
        transform.identity();
        transform.translate((float) pos.x, (float) pos.y, (float) pos.z);
        transform.rotate(quat);
        transform.scale((float) scale.x, (float) scale.y, (float) scale.z);
    }

    /** 获取完整变换矩阵。 */
    public Matrix4f getTransform() { return transform; }

    /** 设置完整变换矩阵（深拷贝）。 */
    public void setTransform(Matrix4f mat) { transform.set(mat); }

    public void setBindToActor(boolean bind) { this.bindToActor = bind; }
    public boolean isBindToActor() { return bindToActor; }

    // ====== 运行时贴图 ======

    /**
     * 设置运行时贴图，后续所有粒子将使用此贴图渲染。
     * 可用于实现动态换贴图（如将粒子贴图改为命中方块的贴图）。
     */
    public void setTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    /** 获取当前运行时贴图。 */
    public ResourceLocation getTexture() {
        return texture;
    }

    /** 获取发射器速度（方块/秒）。*/
    public Vec3 getVelocity() { return velocity; }

    /** 设置发射器速度（方块/秒），用于 emitter_local_space.velocity 粒子继承发射器速度。*/
    public void setVelocity(Vec3 velocity) { this.velocity = velocity; }
}
