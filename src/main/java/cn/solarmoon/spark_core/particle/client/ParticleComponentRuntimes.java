package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.particle.common.data.IEmitterComponent;
import cn.solarmoon.spark_core.particle.common.data.IParticleComponent;
import cn.solarmoon.spark_core.particle.common.data.component.EmitterLocalSpace;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceBillboard;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceLighting;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceTinting;
import cn.solarmoon.spark_core.particle.common.data.component.expire.ParticleExpireIfInBlocks;
import cn.solarmoon.spark_core.particle.common.data.component.expire.ParticleExpireIfNotInBlocks;
import cn.solarmoon.spark_core.particle.common.data.component.init.EmitterInitialization;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialSpeed;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialSpin;
import cn.solarmoon.spark_core.particle.common.data.component.init.ParticleInitialization;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.*;
import cn.solarmoon.spark_core.particle.common.data.component.motion.*;
import cn.solarmoon.spark_core.particle.common.data.component.rate.*;
import cn.solarmoon.spark_core.particle.common.data.component.shape.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 粒子组件运行时工厂。
 * <p>
 * 集中管理所有粒子/发射器组件的运行时实现，将不可变的组件定义（数据模型）
 * 转换为操作 {@link ParticleArray} SoA 数组的实际行为。
 * <p>
 * 每个静态工厂方法接受组件定义和求值环境，返回运行时组件实例。
 * 运行时组件实现 {@link IEmitterComponent} 或 {@link IParticleComponent} 接口，
 * 在发射器引擎的 tick/onSpawn/onApply 管线中被调用。
 */
public class ParticleComponentRuntimes {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleComponentRuntimes.class);
    private static final Random RANDOM = new Random();

    // ==================== 发射器速率组件 ====================

    /**
     * EmitterRateInstant: 瞬时发射——激活时一次性发出所有粒子。
     * 由发射器引擎读取 numParticles 后一次性批量生成。
     */
    public static IEmitterComponent createRateInstant(EmitterRateInstant def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                // 实际产生粒子数由发射器引擎的 emitParticles 逻辑控制
            }

            @Override
            public void onSpawn(ParticleArray buf, int particleIndex, ParticleMolangEnvironment molang) {
                // 无需逐粒子设置
            }
        };
    }

    /**
     * EmitterRateSteady: 稳态发射——每 tick 根据速率表达式生成粒子。
     * 发射器引擎使用 spawnRate 表达式计算每 tick 应产生粒子数。
     */
    public static IEmitterComponent createRateSteady(EmitterRateSteady def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                // 实际粒子生成由发射器引擎处理，此处仅提供速率信息
            }

            @Override
            public void onSpawn(ParticleArray buf, int particleIndex, ParticleMolangEnvironment molang) {}
        };
    }

    /**
     * EmitterRateManual: 手动发射——由外部事件触发，不自发生成粒子。
     */
    public static IEmitterComponent createRateManual(EmitterRateManual def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int particleIndex, ParticleMolangEnvironment molang) {}
        };
    }

    // ==================== 发射器生命周期组件 ====================

    /**
     * EmitterLifetimeOnce: 单次发射——在 activeTime 秒后标记发射器过期。
     * activeTime 为 Molang 表达式，每 tick 求值以支持动态变量引用。
     */
    public static IEmitterComponent createLifetimeOnce(EmitterLifetimeOnce def, ParticleMolangEnvironment molang) {
        // 预编译 activeTime 表达式
        MolangExpression activeTimeExpr = compileIfNotEmpty(def.getActiveTime(), molang);

        return new IEmitterComponent() {
            private float elapsed = 0;

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                elapsed += tickDt;
            }

            /**
             * 查询是否已超过 activeTime。
             * 由发射器引擎在 checkEmitterLifetime 中调用。
             */
            public boolean isExpired() {
                if (activeTimeExpr != null) {
                    return elapsed >= (float) molang.evaluate(activeTimeExpr);
                }
                return false;
            }
        };
    }

    /**
     * EmitterLifetimeLooping: 循环发射——activeTime / sleepTime 交替循环。
     * activeTime / sleepTime 为 Molang 表达式，每 tick 求值以支持动态变量引用。
     */
    public static IEmitterComponent createLifetimeLooping(EmitterLifetimeLooping def, ParticleMolangEnvironment molang) {
        MolangExpression activeTimeExpr = compileIfNotEmpty(def.getActiveTime(), molang);
        MolangExpression sleepTimeExpr = compileIfNotEmpty(def.getSleepTime(), molang);

        return new IEmitterComponent() {
            private float phaseTimer = 0;
            private boolean inActivePhase = true;

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                float activeTime = activeTimeExpr != null ? (float) molang.evaluate(activeTimeExpr) : 10f;
                float sleepTime = sleepTimeExpr != null ? (float) molang.evaluate(sleepTimeExpr) : 0f;

                phaseTimer += tickDt;
                if (inActivePhase) {
                    if (phaseTimer >= activeTime) {
                        inActivePhase = false;
                        phaseTimer = 0;
                    }
                } else {
                    if (phaseTimer >= sleepTime) {
                        inActivePhase = true;
                        phaseTimer = 0;
                    }
                }
            }

            public boolean isInActivePhase() { return inActivePhase; }
        };
    }

    /**
     * EmitterLifetimeExpression: 通过 Molang 表达式控制激活/过期。
     */
    public static IEmitterComponent createLifetimeExpression(EmitterLifetimeExpression def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private final MolangExpression activationExpr = compileIfNotEmpty(def.getActivationExpression(), molang);
            private final MolangExpression expirationExpr = compileIfNotEmpty(def.getExpirationExpression(), molang);
            private boolean hasActivated = false;

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                if (!hasActivated && activationExpr != null) {
                    hasActivated = molang.evaluate(activationExpr) > 0;
                }
                // 发射器引擎检查 hasActivated / expirationExpr 决定是否过期
            }
        };
    }

    /**
     * EmitterLifetimeEvents: 在特定时间点触发发射器事件。
     * 由发射器引擎在 timeline 时间点调用事件执行器。
     */
    public static IEmitterComponent createLifetimeEvents(EmitterLifetimeEvents def) {
        return new IEmitterComponent() {
            private float elapsed = 0;
            private boolean creationFired = false;
            private final java.util.HashSet<Float> firedTimeline = new java.util.HashSet<>();
            private final java.util.HashSet<Float> firedTravelDistance = new java.util.HashSet<>();

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                elapsed += tickDt;
                // 发射器引擎检查 firedTimeline 等触发事件
                // 实际事件执行在 EventExecutor 中完成
            }
        };
    }

    /**
     * EmitterInitialization: 发射器初始化表达式组件。
     * 创建时执行 creation_expression，每 tick 执行 per_update_expression。
     */
    public static IEmitterComponent createEmitterInit(EmitterInitialization def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private boolean hasExecutedCreation = false;
            private final MolangExpression creationExpr = compileIfNotEmpty(def.getCreationExpression(), molang);
            private final MolangExpression perUpdateExpr = compileIfNotEmpty(def.getPerUpdateExpression(), molang);

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                if (!hasExecutedCreation) {
                    if (creationExpr != null) molang.evaluate(creationExpr);
                    hasExecutedCreation = true;
                }
                if (perUpdateExpr != null) molang.evaluate(perUpdateExpr);
            }
        };
    }

    /**
     * EmitterLocalSpace: 局部空间标记组件。
     * 运行时无 tick 行为，其标志位在 EmitterPreset 中直接读取。
     */
    public static IEmitterComponent createLocalSpace(EmitterLocalSpace def) {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}
        };
    }

    // ==================== 发射器形状组件 ====================
    //
    // 形状 onSpawn 只计算相对于 emitter 的偏移量（shape offset），
    // emitter 的位置叠加由 ParticleEmitterInstance.emitParticles() 统一处理。
    // 这样做的好处是：
    //   1. 形状组件不依赖 emitter 实例，可在反序列化时创建
    //   2. emitParticles 根据 hasLocalPosition 决定是否叠加 emitterPos

    /**
     * EmitterShapePoint: 点发射——粒子从单个点位置发射。
     */
    public static IEmitterComponent createShapePoint(EmitterShapePoint def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private final MolangExpression offX = molang.compile(def.getOffset()[0]);
            private final MolangExpression offY = molang.compile(def.getOffset()[1]);
            private final MolangExpression offZ = molang.compile(def.getOffset()[2]);
            private final MolangExpression dirX = molang.compile(def.getDirection()[0]);
            private final MolangExpression dirY = molang.compile(def.getDirection()[1]);
            private final MolangExpression dirZ = molang.compile(def.getDirection()[2]);

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int idx, ParticleMolangEnvironment molang) {
                buf.setPos(idx,
                        (float) molang.evaluate(offX),
                        (float) molang.evaluate(offY),
                        (float) molang.evaluate(offZ));
                buf.setVel(idx,
                        (float) molang.evaluate(dirX),
                        (float) molang.evaluate(dirY),
                        (float) molang.evaluate(dirZ));
            }
        };
    }

    /**
     * EmitterShapeSphere: 球体发射——粒子从球体表面或内部随机位置发射。
     */
    public static IEmitterComponent createShapeSphere(EmitterShapeSphere def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private final MolangExpression radiusExpr = molang.compile(def.getRadius());
            private final MolangExpression offX = molang.compile(def.getOffset()[0]);
            private final MolangExpression offY = molang.compile(def.getOffset()[1]);
            private final MolangExpression offZ = molang.compile(def.getOffset()[2]);
            private final MolangExpression dirX = molang.compile(def.getDirection()[0]);
            private final MolangExpression dirY = molang.compile(def.getDirection()[1]);
            private final MolangExpression dirZ = molang.compile(def.getDirection()[2]);

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int idx, ParticleMolangEnvironment molang) {
                float radius = (float) molang.evaluate(radiusExpr);
                float ox = (float) molang.evaluate(offX);
                float oy = (float) molang.evaluate(offY);
                float oz = (float) molang.evaluate(offZ);

                // 球坐标采样
                double theta = RANDOM.nextDouble() * 2 * Math.PI;
                double phi = Math.acos(2 * RANDOM.nextDouble() - 1);
                double r = def.isSurfaceOnly() ? radius : radius * Math.cbrt(RANDOM.nextDouble());

                float dx = (float) (r * Math.sin(phi) * Math.cos(theta));
                float dy = (float) (r * Math.cos(phi));
                float dz = (float) (r * Math.sin(phi) * Math.sin(theta));

                if (def.isDirectionInwards()) {
                    buf.setPos(idx, ox - dx, oy - dy, oz - dz);
                    buf.setVel(idx, -dx, -dy, -dz);
                } else {
                    buf.setPos(idx, ox + dx, oy + dy, oz + dz);
                    buf.setVel(idx,
                            (float) molang.evaluate(dirX) + dx,
                            (float) molang.evaluate(dirY) + dy,
                            (float) molang.evaluate(dirZ) + dz);
                }
            }
        };
    }

    /**
     * EmitterShapeBox: 立方体发射——粒子从长方体表面或内部随机位置发射。
     */
    public static IEmitterComponent createShapeBox(EmitterShapeBox def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private final MolangExpression hx = molang.compile(def.getHalfDimensions()[0]);
            private final MolangExpression hy = molang.compile(def.getHalfDimensions()[1]);
            private final MolangExpression hz = molang.compile(def.getHalfDimensions()[2]);
            private final MolangExpression offX = molang.compile(def.getOffset()[0]);
            private final MolangExpression offY = molang.compile(def.getOffset()[1]);
            private final MolangExpression offZ = molang.compile(def.getOffset()[2]);

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int idx, ParticleMolangEnvironment molang) {
                float sx = (float) molang.evaluate(hx);
                float sy = (float) molang.evaluate(hy);
                float sz = (float) molang.evaluate(hz);
                float ox = (float) molang.evaluate(offX);
                float oy = (float) molang.evaluate(offY);
                float oz = (float) molang.evaluate(offZ);

                float dx, dy, dz;
                if (def.isSurfaceOnly()) {
                    int face = RANDOM.nextInt(6);
                    dx = switch (face) {
                        case 0 -> sx;  case 1 -> -sx;
                        default -> (RANDOM.nextFloat() - 0.5f) * 2 * sx;
                    };
                    dy = switch (face) {
                        case 2 -> sy;  case 3 -> -sy;
                        default -> (RANDOM.nextFloat() - 0.5f) * 2 * sy;
                    };
                    dz = switch (face) {
                        case 4 -> sz;  case 5 -> -sz;
                        default -> (RANDOM.nextFloat() - 0.5f) * 2 * sz;
                    };
                } else {
                    dx = (RANDOM.nextFloat() - 0.5f) * 2 * sx;
                    dy = (RANDOM.nextFloat() - 0.5f) * 2 * sy;
                    dz = (RANDOM.nextFloat() - 0.5f) * 2 * sz;
                }

                buf.setPos(idx, ox + dx, oy + dy, oz + dz);
            }
        };
    }

    /**
     * EmitterShapeDisc: 圆盘发射——粒子从圆盘表面或内部随机位置发射。
     */
    public static IEmitterComponent createShapeDisc(EmitterShapeDisc def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            private final MolangExpression radiusExpr = molang.compile(def.getRadius());
            private final MolangExpression offX = molang.compile(def.getOffset()[0]);
            private final MolangExpression offY = molang.compile(def.getOffset()[1]);
            private final MolangExpression offZ = molang.compile(def.getOffset()[2]);

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int idx, ParticleMolangEnvironment molang) {
                float radius = (float) molang.evaluate(radiusExpr);
                float ox = (float) molang.evaluate(offX);
                float oy = (float) molang.evaluate(offY);
                float oz = (float) molang.evaluate(offZ);

                double theta = RANDOM.nextDouble() * 2 * Math.PI;
                double r = def.isSurfaceOnly() ? radius : radius * Math.sqrt(RANDOM.nextDouble());

                float dx = (float) (r * Math.cos(theta));
                float dz = (float) (r * Math.sin(theta));

                buf.setPos(idx, ox + dx, oy, oz + dz);
            }
        };
    }

    /**
     * EmitterShapeEntityAABB: 实体包围盒发射——粒子从绑定实体的 AABB 内随机位置发射。
     */
    public static IEmitterComponent createShapeEntityAABB(EmitterShapeEntityAABB def, ParticleMolangEnvironment molang) {
        return new IEmitterComponent() {
            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {}

            @Override
            public void onSpawn(ParticleArray buf, int idx, ParticleMolangEnvironment molang) {
                buf.setPos(idx,
                        (RANDOM.nextFloat() - 0.5f),
                        (RANDOM.nextFloat() - 0.5f),
                        (RANDOM.nextFloat() - 0.5f));
            }
        };
    }

    // ==================== 粒子运动组件 ====================

    /**
     * ParticleMotionDynamic: 动力学运动——通过 Molang 表达式设置加速度和阻力系数，
     * 由 {@link CollisionResolver#move} 执行运动积分。
     */
    public static IParticleComponent createMotionDynamic(ParticleMotionDynamic def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression accelX = molang.compile(def.getLinearAccelerationX());
            private final MolangExpression accelY = molang.compile(def.getLinearAccelerationY());
            private final MolangExpression accelZ = molang.compile(def.getLinearAccelerationZ());
            private final MolangExpression drag = molang.compile(def.getLinearDragCoefficient());
            private final MolangExpression rotAccel = molang.compile(def.getRotationAcceleration());
            private final MolangExpression rotDrag = molang.compile(def.getRotationDragCoefficient());

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                buf.setAccelX(index, (float) molang.evaluate(accelX));
                buf.setAccelY(index, (float) molang.evaluate(accelY));
                buf.setAccelZ(index, (float) molang.evaluate(accelZ));
                buf.setDragCoeff(index, (float) molang.evaluate(drag));
                buf.setRotAccel(index, (float) molang.evaluate(rotAccel));
                // 旋转阻力通过拖拽系数应用到 rotRate（由 CollisionResolver.move 处理）
                float rd = (float) molang.evaluate(rotDrag);
                if (rd > 0) {
                    float rate = buf.getRotRate(index);
                    buf.setRotRate(index, rate * Math.max(0, 1 - rd * tickDt));
                }
            }
        };
    }

    /**
     * ParticleMotionParametric: 参数化运动——直接用 Molang 表达式控制粒子每 tick 的位置和朝向。
     * 对标 SBM：relative_position 控制位置，direction 控制朝向，均覆盖速度/加速度。
     */
    public static IParticleComponent createMotionParametric(ParticleMotionParametric def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression posX = molang.compile(def.getRelativePosition()[0]);
            private final MolangExpression posY = molang.compile(def.getRelativePosition()[1]);
            private final MolangExpression posZ = molang.compile(def.getRelativePosition()[2]);
            private final MolangExpression rot = molang.compile(def.getRotation());
            private final MolangExpression dirX = molang.compile(def.getDirection()[0]);
            private final MolangExpression dirY = molang.compile(def.getDirection()[1]);
            private final MolangExpression dirZ = molang.compile(def.getDirection()[2]);

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                buf.setPos(index,
                        (float) molang.evaluate(posX),
                        (float) molang.evaluate(posY),
                        (float) molang.evaluate(posZ));
                buf.setRot(index, (float) molang.evaluate(rot));
                // direction 表达式设置朝向速度方向
                buf.setVel(index,
                        (float) molang.evaluate(dirX),
                        (float) molang.evaluate(dirY),
                        (float) molang.evaluate(dirZ));
                // 参数化运动覆盖位置/速度，清零加速度防止运动积分干扰
                buf.setAccel(index, 0, 0, 0);
            }
        };
    }

    /**
     * ParticleMotionCollision: 碰撞参数写入器。
     * <p>
     * 本组件**不做检测、不改速度与位置**，只把每 tick 求值出的
     * radius / restitution / drag / 标志位写入 {@link ParticleArray}；
     * 位移积分与 AABB 扫掠由 {@link CollisionResolver#move} 在所有组件 tick 之后统一执行一次。
     * <p>
     * 非世界空间的预设（`emitter_local_space.position=true` 或含 `particle_motion_parametric`）
     * 不会创建本组件，见 ParticleEffectDeserializer 的构建期门控。
     */
    public static IParticleComponent createMotionCollision(ParticleMotionCollision def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression enabledExpr = molang.compile(def.getEnabled());
            private final MolangExpression dragExpr = molang.compile(def.getCollisionDrag());
            private final MolangExpression restitutionExpr = molang.compile(def.getCoefficientOfRestitution());
            private final MolangExpression radiusExpr = molang.compile(def.getCollisionRadius());
            private final boolean expireOnContact = def.isExpireOnContact();
            private boolean warnedRadius = false;

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment ctx) {
                if (!buf.isAlive(index)) return;

                // 读-改-写：保留 CollisionResolver 写入的接触位，其余位每 tick 重算
                byte flags = (byte) (buf.getCollFlags(index) & ParticleArray.COLL_CONTACT);
                if (ctx.evaluate(enabledExpr) != 0) {
                    flags |= ParticleArray.COLL_ENABLED;

                    float radius = (float) ctx.evaluate(radiusExpr);
                    // 规范上限按**表达式书写值**判定：0.5 是 Bedrock 对 collision_radius 的硬性规定，
                    // 与发射器缩放无关，不应因缩放而改变告警判定。
                    if (radius > CollisionResolver.MAX_COLLISION_RADIUS) {
                        if (!warnedRadius) {
                            warnedRadius = true;
                            LOGGER.warn("collision_radius 表达式 \"{}\" 求得 {}，超过基岩规范上限 {}（1/2 格），已夹取",
                                    def.getCollisionRadius(), radius, CollisionResolver.MAX_COLLISION_RADIUS);
                        }
                        radius = CollisionResolver.MAX_COLLISION_RADIUS;
                    } else if (!(radius > 0f)) {
                        radius = 0f;   // 负值 / NaN 归零，避免碰撞盒被缩小到基础尺寸以下
                    }
                    // 发射器缩放：碰撞盒与贴图同步放大，否则放大后的贴图会视觉上陷入方块。
                    // 倍率取出生时烘焙值（与同粒子的尺寸同源），使发射器中途改变缩放不会让已飞出的
                    // 粒子碰撞盒突变；上限（已按规范夹到 ≤0.5）随缩放等比放宽，语义上等价于
                    // "0.5 是缩放前的规格上限"，且 AABB 尺寸保护随之同步。
                    float scale = buf.getSizeScale(index);
                    buf.setCollRadius(index, radius * scale);
                    buf.setCollRestitution(index, (float) ctx.evaluate(restitutionExpr));
                    buf.setCollDrag(index, (float) ctx.evaluate(dragExpr));
                }
                if (expireOnContact) flags |= ParticleArray.COLL_EXPIRE;
                buf.setCollFlags(index, flags);
            }
        };
    }

    // ==================== 粒子外观组件 ====================

    /**
     * ParticleAppearanceBillboard: 公告板外观——设置粒子的尺寸和 UV 坐标。
     * 翻页书动画根据粒子年龄/生命周期计算当前帧。
     * 所有 Flipbook 数值字段均编译为 MoLang 表达式，每 tick 求值（对齐 SBM）。
     */
    public static IParticleComponent createAppearanceBillboard(ParticleAppearanceBillboard def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression sizeW = molang.compile(def.getSize()[0]);
            private final MolangExpression sizeH = molang.compile(def.getSize()[1]);
            private final boolean hasUv = def.getUv() != null;
            private final boolean hasFlipbook = def.getFlipbook() != null;

            // Flipbook MoLang 表达式（仅在 hasFlipbook 时使用）
            private final MolangExpression fbBaseU = hasFlipbook ? molang.compile(def.getFlipbook().getBaseU()) : null;
            private final MolangExpression fbBaseV = hasFlipbook ? molang.compile(def.getFlipbook().getBaseV()) : null;
            private final MolangExpression fbSizeX = hasFlipbook ? molang.compile(def.getFlipbook().getSizeX()) : null;
            private final MolangExpression fbSizeY = hasFlipbook ? molang.compile(def.getFlipbook().getSizeY()) : null;
            private final MolangExpression fbStepX = hasFlipbook ? molang.compile(def.getFlipbook().getStepX()) : null;
            private final MolangExpression fbStepY = hasFlipbook ? molang.compile(def.getFlipbook().getStepY()) : null;
            private final MolangExpression fbFps = hasFlipbook ? molang.compile(def.getFlipbook().getFps()) : null;
            private final MolangExpression fbMaxFrame = hasFlipbook ? molang.compile(def.getFlipbook().getMaxFrame()) : null;
            /** 翻页书 UV 复用的 4 元素暂存器（零分配） */
            private final float[] fbUvScratch = new float[4];

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                // 乘出生尺寸倍率：世界模式为发射器缩放（在发射时烘焙，与位置/速度同源），
                // 局部空间模式为 1（缩放已由渲染期父矩阵作用于 quad）
                float sizeScale = buf.getSizeScale(index);
                buf.setWidth(index, (float) molang.evaluate(sizeW) * sizeScale);
                buf.setHeight(index, (float) molang.evaluate(sizeH) * sizeScale);

                if (hasUv) {
                    var uv = def.getUv();
                    // UV 已在反序列化时归一化到 [0,1]，直接使用
                    buf.setUV(index, uv.getU0(), uv.getV0(), uv.getU1(), uv.getV1());
                }
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                // 每 tick 更新尺寸（支持动态 Molang 表达式）；
                // 倍率沿用出生时烘焙的值，使发射器中途改变缩放不会让已飞出的粒子突变尺寸
                float sizeScale = buf.getSizeScale(index);
                buf.setWidth(index, (float) molang.evaluate(sizeW) * sizeScale);
                buf.setHeight(index, (float) molang.evaluate(sizeH) * sizeScale);

                if (hasFlipbook) {
                    float age = buf.getAge(index);
                    float maxLife = buf.getMaxLifetime(index);

                    // 运行时求值所有 Flipbook Molang 表达式（像素坐标）
                    float baseU = (float) molang.evaluate(fbBaseU);
                    float baseV = (float) molang.evaluate(fbBaseV);
                    float sizeX = (float) molang.evaluate(fbSizeX);
                    float sizeY = (float) molang.evaluate(fbSizeY);
                    float stepX = (float) molang.evaluate(fbStepX);
                    float stepY = (float) molang.evaluate(fbStepY);
                    float fps = (float) molang.evaluate(fbFps);
                    int maxFrame = Math.round((float) molang.evaluate(fbMaxFrame));

                    // 帧号：stretch_to_lifetime 时整个生命周期播放一遍，否则按 fps 推进
                    int frame = flipbookFrame(age, maxLife, fps, maxFrame,
                            def.getFlipbook().isStretchToLifetime(), def.getFlipbook().isLoop());

                    // 像素坐标 → 归一化 UV（与静态 uv/uv_size 同一口径）
                    flipbookUv(fbUvScratch, baseU, baseV, sizeX, sizeY, stepX, stepY, frame,
                            def.getFlipbook().getTextureWidth(), def.getFlipbook().getTextureHeight());
                    buf.setUV(index, fbUvScratch[0], fbUvScratch[1], fbUvScratch[2], fbUvScratch[3]);
                }
            }
        };
    }

    /**
     * 翻页书当前帧号。
     * <ul>
     *   <li>{@code stretch_to_lifetime}：把 0..1 的生命周期进度映射到 {@code 0..maxFrame}（整段寿命播放一遍）；</li>
     *   <li>否则按 {@code age * fps} 推进；</li>
     *   <li>{@code loop} 时对 {@code maxFrame} 取模，否则截断到 {@code [0, maxFrame - 1]}。</li>
     * </ul>
     * 包级可见以便单测（{@code maxFrame <= 0} 表示未指定帧数，此时不做取模/截断）。
     */
    static int flipbookFrame(float age, float maxLife, float fps, int maxFrame,
                             boolean stretchToLifetime, boolean loop) {
        int frame;
        if (stretchToLifetime && maxLife > 0f) {
            // 未指定 max_frame 时映射不出帧号，停在第一帧（而不是退化成按 fps 无界推进）
            frame = maxFrame > 0 ? (int) (age / maxLife * maxFrame) : 0;
        } else {
            frame = (int) (age * fps);
        }
        if (maxFrame > 0) {
            frame = loop ? Math.floorMod(frame, maxFrame) : Math.min(Math.max(frame, 0), maxFrame - 1);
        }
        return frame;
    }

    /**
     * 把翻页书帧的 UV 写入 {@code out}（长度 ≥ 4，顺序 u0/v0/u1/u2），归一化到 [0,1]。
     * <p>
     * 翻页书的 UV 三元组与同组件的 {@code uv}/{@code uv_size} 一样是**像素**坐标，必须按纹理尺寸归一化
     * —— 直接写像素值会让 UV 落在 [0,1] 之外。包级可见以便单测。
     */
    static void flipbookUv(float[] out, float baseU, float baseV, float sizeX, float sizeY,
                           float stepX, float stepY, int frame, float texW, float texH) {
        if (!(texW > 0f)) texW = 1f;
        if (!(texH > 0f)) texH = 1f;
        float u = (baseU + stepX * frame) / texW;
        float v = (baseV + stepY * frame) / texH;
        out[0] = u;
        out[1] = v;
        out[2] = u + sizeX / texW;
        out[3] = v + sizeY / texH;
    }

    /**
     * ParticleAppearanceTinting: 着色外观——控制粒子 RGBA 颜色。
     * 支持 solid（纯色，Molang 表达式）和 gradient/ramp_gradient（渐变色标插值）。
     */
    public static IParticleComponent createAppearanceTinting(ParticleAppearanceTinting def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            // solid 模式表达式
            private final MolangExpression solidR = def.getColor() != null && "solid".equals(def.getColor().getMode())
                    ? molang.compile(def.getColor().getR()) : null;
            private final MolangExpression solidG = def.getColor() != null && "solid".equals(def.getColor().getMode())
                    ? molang.compile(def.getColor().getG()) : null;
            private final MolangExpression solidB = def.getColor() != null && "solid".equals(def.getColor().getMode())
                    ? molang.compile(def.getColor().getB()) : null;
            private final MolangExpression solidA = def.getColor() != null && "solid".equals(def.getColor().getMode())
                    ? molang.compile(def.getColor().getA()) : null;

            // gradient 模式
            private final boolean isGradient = def.getColor() != null &&
                    ("gradient".equals(def.getColor().getMode()) || "ramp_gradient".equals(def.getColor().getMode()));
            private final MolangExpression interpolant = isGradient ? molang.compile(def.getColor().getInterpolant()) : null;
            private final List<ParticleAppearanceTinting.ColorStop> gradientStops = isGradient
                    ? def.getColor().getGradient() : null;

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                if (def.getColor() == null) return;

                if (solidR != null) {
                    buf.setColor(index,
                            (float) molang.evaluate(solidR),
                            (float) molang.evaluate(solidG),
                            (float) molang.evaluate(solidB),
                            (float) molang.evaluate(solidA));
                } else if (isGradient && gradientStops != null && !gradientStops.isEmpty()) {
                    // 渐变模式：默认使用起始颜色（含 alpha）
                    float[] rgb = parseHexColor(gradientStops.get(0).getColor());
                    buf.setColor(index, rgb[0], rgb[1], rgb[2], rgb[3]);
                }
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                if (def.getColor() == null) return;

                if (solidR != null) {
                    // 每 tick 重新求值以支持动态变量引用
                    buf.setColor(index,
                            (float) molang.evaluate(solidR),
                            (float) molang.evaluate(solidG),
                            (float) molang.evaluate(solidB),
                            (float) molang.evaluate(solidA));
                } else if (isGradient && gradientStops != null && gradientStops.size() >= 2) {
                    float progress = (float) molang.evaluate(interpolant);
                    float[] rgb = interpolateColorStops(gradientStops, progress);
                    // 使用插值计算出的 alpha（含渐变梯度中不同 stop 的 alpha 过渡）
                    buf.setColor(index, rgb[0], rgb[1], rgb[2], rgb[3]);
                }
            }
        };
    }

    /**
     * ParticleAppearanceLighting: 光照外观——标记组件，无运行时行为。
     * 光照标记位在 ParticlePreset 中直接读取。
     */
    public static IParticleComponent createAppearanceLighting(ParticleAppearanceLighting def) {
        return new IParticleComponent() {
            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {}

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {}
        };
    }

    // ==================== 粒子初始化组件 ====================

    /**
     * ParticleInitialization: 粒子初始化——每 tick 执行 per_update_expression。
     * per_render_expression 由渲染管线在渲染线程调用。
     */
    public static IParticleComponent createParticleInit(ParticleInitialization def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression perUpdateExpr = compileIfNotEmpty(def.getPerUpdateExpression(), molang);

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                // 粒子生成时无特殊操作
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                if (perUpdateExpr != null) {
                    molang.evaluate(perUpdateExpr);
                }
            }
        };
    }

    /**
     * ParticleInitialSpeed: 初始速度——粒子生成时将当前速度方向缩放到指定速率。
     */
    public static IParticleComponent createInitialSpeed(ParticleInitialSpeed def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression speedExpr = molang.compile(def.getSpeed());

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                float speed = (float) molang.evaluate(speedExpr);
                float vx = buf.getVelX(index);
                float vy = buf.getVelY(index);
                float vz = buf.getVelZ(index);
                float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len > 0.0001f) {
                    float scale = speed / len;
                    buf.setVel(index, vx * scale, vy * scale, vz * scale);
                } else {
                    // 无初始方向时沿 +Z 方向
                    buf.setVel(index, 0, 0, speed);
                }
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                // 初始速度仅在生成时应用一次
            }
        };
    }

    /**
     * ParticleInitialSpin: 初始旋转——设置粒子的初始旋转角度和旋转速率。
     */
    public static IParticleComponent createInitialSpin(ParticleInitialSpin def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression rotExpr = molang.compile(def.getRotation());
            private final MolangExpression rateExpr = molang.compile(def.getRotationRate());

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                buf.setRot(index, (float) molang.evaluate(rotExpr));
                buf.setRotRate(index, (float) molang.evaluate(rateExpr));
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {}
        };
    }

    // ==================== 粒子生命周期组件 ====================

    /**
     * ParticleLifetimeExpression: 生命周期表达式——通过 Molang 表达式设置粒子寿命。
     * max_lifetime 决定粒子能存活多久，expiration_expression 提供额外过期条件。
     */
    public static IParticleComponent createLifetimeExpression(ParticleLifetimeExpression def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression maxLifeExpr = molang.compile(def.getMaxLifetime());
            private final MolangExpression expirationExpr = compileIfNotEmpty(def.getExpirationExpression(), molang);

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                float maxLife = (float) molang.evaluate(maxLifeExpr);
                buf.setMaxLifetime(index, Math.max(maxLife, 0.001f));
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                // 检查额外过期条件
                if (expirationExpr != null && molang.evaluate(expirationExpr) > 0) {
                    buf.markDead(index);
                }
            }
        };
    }

    /**
     * ParticleLifetimeEvents: 生命周期事件——在粒子创建/过期/时间线时触发事件。
     * 事件执行由 EventExecutor 完成，运行时组件记录事件定义供引擎查询。
     */
    public static IParticleComponent createLifetimeEvents(ParticleLifetimeEvents def) {
        return new IParticleComponent() {
            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                // creationEvent 由发射器引擎在生成时触发
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                // 发射器引擎根据 timeline 检查触发事件
            }
        };
    }

    /**
     * ParticleLifetimeKillPlane: 杀戮平面——粒子穿过任一平面时过期。
     * 每个平面由 4 个系数 (a, b, c, d) 定义：ax + by + cz + d = 0。
     * 对标 SBM/Bedrock：裸数组 [a,b,c,d] 格式，可多平面。
     */
    public static IParticleComponent createKillPlane(ParticleLifetimeKillPlane def) {
        return new IParticleComponent() {
            private final List<float[]> planes = def.getPlanes();

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                float x = buf.getPosX(index);
                float y = buf.getPosY(index);
                float z = buf.getPosZ(index);

                for (float[] p : planes) {
                    float val = p[0] * x + p[1] * y + p[2] * z + p[3];
                    if (val <= 0) {
                        buf.markDead(index);
                        return;
                    }
                }
            }
        };
    }

    // ==================== 粒子过期条件组件 ====================

    /**
     * ParticleExpireIfInBlocks: 在方块内过期——粒子位于指定方块列表中时过期。
     */
    public static IParticleComponent createExpireIfInBlocks(ParticleExpireIfInBlocks def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final Set<String> blockIds = def.getBlocks();

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment ctx) {
                Level level = ctx.getLevel();
                if (level == null) return;
                float x = buf.getPosX(index);
                float y = buf.getPosY(index);
                float z = buf.getPosZ(index);
                BlockPos pos = BlockPos.containing(x, y, z);
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                if (blockIds.contains(blockId)) {
                    buf.markDead(index);
                }
            }
        };
    }

    /**
     * ParticleExpireIfNotInBlocks: 不在方块内过期——粒子不在指定方块列表中时过期。
     */
    public static IParticleComponent createExpireIfNotInBlocks(ParticleExpireIfNotInBlocks def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final Set<String> blockIds = def.getBlocks();

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment ctx) {
                Level level = ctx.getLevel();
                if (level == null) return;
                float x = buf.getPosX(index);
                float y = buf.getPosY(index);
                float z = buf.getPosZ(index);
                BlockPos pos = BlockPos.containing(x, y, z);
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                if (!blockIds.contains(blockId)) {
                    buf.markDead(index);
                }
            }
        };
    }

    // ==================== 颜色工具方法 ====================

    /**
     * 解析十六进制颜色字符串为 RGBA float 数组 [0, 1]。
     * 支持 #RGB、#RRGGBB 和 #AARRGGBB（基岩版标准，Alpha 在前）。
     */
    private static float[] parseHexColor(String hex) {
        try {
            String clean = hex.replace("#", "");
            int rgb;
            float alpha = 1;
            switch (clean.length()) {
                case 3 -> {
                    // #RGB -> #RRGGBB
                    StringBuilder sb = new StringBuilder();
                    for (char c : clean.toCharArray()) {
                        sb.append(c).append(c);
                    }
                    rgb = Integer.parseInt(sb.toString(), 16);
                }
                case 8 -> {
                    // #AARRGGBB — 前2位 AA，后6位 RRGGBB（基岩版标准）
                    alpha = Integer.parseInt(clean.substring(0, 2), 16) / 255f;
                    rgb = (int) Long.parseLong(clean.substring(2, 8), 16);
                }
                default -> rgb = Integer.parseInt(clean.substring(0, 6), 16);
            }
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255f,
                    ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f,
                    alpha
            };
        } catch (Exception e) {
            return new float[]{1, 1, 1, 1};
        }
    }

    /**
     * 在渐变色标列表中进行线性插值，返回指定进度 t 处的颜色。
     * 将 t 钳制到 [stops[0], stops[last]] 范围内（对齐 SBM/ParticleTintingGradient）。
     */
    private static float[] interpolateColorStops(List<ParticleAppearanceTinting.ColorStop> stops, float t) {
        if (stops == null || stops.isEmpty()) return new float[]{1, 1, 1, 1};
        if (stops.size() == 1) return parseHexColor(stops.get(0).getColor());

        float firstPos = stops.get(0).getPosition();
        float lastPos = stops.get(stops.size() - 1).getPosition();
        // 将 t 钳制到 stops 的有效范围 [firstPos, lastPos]
        t = Math.max(firstPos, Math.min(lastPos, t));

        for (int i = 0; i < stops.size() - 1; i++) {
            float p0 = stops.get(i).getPosition();
            float p1 = stops.get(i + 1).getPosition();
            if (t >= p0 && t <= p1) {
                float localT = p1 > p0 ? (t - p0) / (p1 - p0) : 0;
                float[] c0 = parseHexColor(stops.get(i).getColor());
                float[] c1 = parseHexColor(stops.get(i + 1).getColor());
                return new float[]{
                        c0[0] + (c1[0] - c0[0]) * localT,
                        c0[1] + (c1[1] - c0[1]) * localT,
                        c0[2] + (c1[2] - c0[2]) * localT,
                        c0[3] + (c1[3] - c0[3]) * localT
                };
            }
        }
        // 理论上不会执行到这里（t 已被钳制到范围内），回退到最后一个 stop
        return parseHexColor(stops.get(stops.size() - 1).getColor());
    }

    // ==================== 工具方法 ====================

    /**
     * 编译非空表达式，空或 null 字符串返回 null。
     */
    private static MolangExpression compileIfNotEmpty(String expr, ParticleMolangEnvironment molang) {
        if (expr == null || expr.isEmpty()) return null;
        return molang.compile(expr);
    }
}
