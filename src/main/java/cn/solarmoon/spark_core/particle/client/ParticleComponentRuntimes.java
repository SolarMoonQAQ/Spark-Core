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

import java.util.List;
import java.util.Random;

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
     */
    public static IEmitterComponent createLifetimeOnce(EmitterLifetimeOnce def) {
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
                return elapsed >= def.getActiveTime();
            }
        };
    }

    /**
     * EmitterLifetimeLooping: 循环发射——activeTime / sleepTime 交替循环。
     */
    public static IEmitterComponent createLifetimeLooping(EmitterLifetimeLooping def) {
        return new IEmitterComponent() {
            private float phaseTimer = 0;
            private boolean inActivePhase = true;

            @Override
            public void tick(ParticleArray buf, float tickDt, ParticleMolangEnvironment molang) {
                phaseTimer += tickDt;
                if (inActivePhase) {
                    if (phaseTimer >= def.getActiveTime()) {
                        inActivePhase = false;
                        phaseTimer = 0;
                    }
                } else {
                    if (phaseTimer >= def.getSleepTime()) {
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
     * 由 ParticleArray.integrateParticle() 执行运动积分。
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
                // 旋转阻力通过拖拽系数应用到 rotRate（由 integrateParticle 处理）
                float rd = (float) molang.evaluate(rotDrag);
                if (rd > 0) {
                    float rate = buf.getRotRate(index);
                    buf.setRotRate(index, rate * Math.max(0, 1 - rd * tickDt));
                }
            }
        };
    }

    /**
     * ParticleMotionParametric: 参数化运动——直接用 Molang 表达式控制粒子每 tick 的位置。
     * 会清零速度和加速度以阻止常规运动积分干扰。
     */
    public static IParticleComponent createMotionParametric(ParticleMotionParametric def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression posX = molang.compile(def.getRelativePosition()[0]);
            private final MolangExpression posY = molang.compile(def.getRelativePosition()[1]);
            private final MolangExpression posZ = molang.compile(def.getRelativePosition()[2]);
            private final MolangExpression rot = molang.compile(def.getRotation());

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                buf.setPos(index,
                        (float) molang.evaluate(posX),
                        (float) molang.evaluate(posY),
                        (float) molang.evaluate(posZ));
                buf.setRot(index, (float) molang.evaluate(rot));
                // 参数化运动覆盖位置，清零速度和加速度防止运动积分干扰
                buf.setVel(index, 0, 0, 0);
                buf.setAccel(index, 0, 0, 0);
            }
        };
    }

    /**
     * ParticleMotionCollision: 碰撞运动——检测粒子与方块的碰撞并反弹。
     * 需要 Level 访问权限进行方块碰撞检测。
     */
    public static IParticleComponent createMotionCollision(ParticleMotionCollision def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final float drag = def.getCollisionDrag();
            private final float restitution = def.getCoefficientOfRestitution();
            private final float radius = def.getCollisionRadius();
            private final boolean expireOnContact = def.isExpireOnContact();

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment ctx) {
                if (!buf.isAlive(index)) return;

                Level level = ctx.getLevel();
                if (level == null) return; // 服务端或 Level 不可用时跳过

                float px = buf.getPosX(index);
                float py = buf.getPosY(index);
                float pz = buf.getPosZ(index);
                float vx = buf.getVelX(index);
                float vy = buf.getVelY(index);
                float vz = buf.getVelZ(index);

                // 简单方块碰撞检测
                BlockPos blockPos = BlockPos.containing(px, py, pz);
                if (!level.getBlockState(blockPos).isAir()) {
                    // 计算反弹方向（简化为沿法线反转）
                    // 精确碰撞检测需要更复杂的算法，此处为基础实现
                    if (expireOnContact) {
                        buf.markDead(index);
                        return;
                    }
                    // 反转速度并施加阻力
                    buf.setVel(index, -vx * (1 - drag), -vy * (1 - drag), -vz * (1 - drag));
                }
            }
        };
    }

    // ==================== 粒子外观组件 ====================

    /**
     * ParticleAppearanceBillboard: 公告板外观——设置粒子的尺寸和 UV 坐标。
     * 翻页书动画根据粒子年龄/生命周期计算当前帧。
     */
    public static IParticleComponent createAppearanceBillboard(ParticleAppearanceBillboard def, ParticleMolangEnvironment molang) {
        return new IParticleComponent() {
            private final MolangExpression sizeW = molang.compile(def.getSize()[0]);
            private final MolangExpression sizeH = molang.compile(def.getSize()[1]);
            private final boolean hasUv = def.getUv() != null;
            private final boolean hasFlipbook = def.getFlipbook() != null;

            @Override
            public void onApply(ParticleArray buf, int index, ParticleMolangEnvironment molang) {
                buf.setWidth(index, (float) molang.evaluate(sizeW));
                buf.setHeight(index, (float) molang.evaluate(sizeH));

                if (hasUv) {
                    var uv = def.getUv();
                    float u0 = uv.getU0();
                    float v0 = uv.getV0();
                    float u1 = uv.getU1();
                    float v1 = uv.getV1();
                    // 如果 UV 指定了纹理尺寸，转换到归一化坐标
                    if (uv.getTextureSizeX() != null && uv.getTextureSizeY() != null) {
                        float texW = uv.getTextureSizeX();
                        float texH = uv.getTextureSizeY();
                        buf.setUV(index, u0 / texW, v0 / texH, u1 / texW, v1 / texH);
                    } else {
                        buf.setUV(index, u0, v0, u1, v1);
                    }
                }
            }

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                // 每 tick 更新尺寸（支持动态 Molang 表达式）
                buf.setWidth(index, (float) molang.evaluate(sizeW));
                buf.setHeight(index, (float) molang.evaluate(sizeH));

                if (hasFlipbook) {
                    var fb = def.getFlipbook();
                    float age = buf.getAge(index);
                    float maxLife = buf.getMaxLifetime(index);
                    float progress;
                    if (fb.isStretchToLifetime() && maxLife > 0) {
                        progress = age / maxLife;
                    } else {
                        progress = age * fb.getFps();
                    }
                    int frame = (int) progress;
                    if (fb.getMaxFrame() > 0) {
                        frame = fb.isLoop() ? frame % fb.getMaxFrame() : Math.min(frame, fb.getMaxFrame() - 1);
                    }
                    float u = fb.getBaseU() + fb.getStepX() * frame;
                    float v = fb.getBaseV() + fb.getStepY() * frame;
                    buf.setUV(index, u, v, u + fb.getSizeX(), v + fb.getSizeY());
                }
            }
        };
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
                    // 渐变模式：默认使用起始颜色
                    float[] rgb = parseHexColor(gradientStops.get(0).getColor());
                    buf.setColor(index, rgb[0], rgb[1], rgb[2], 1);
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
                    buf.setColor(index, rgb[0], rgb[1], rgb[2], buf.getA(index));
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
     * ParticleLifetimeKillPlane: 杀戮平面——粒子穿过指定平面时过期。
     * 每个平面由 4 个系数 (a, b, c, d) 定义：ax + by + cz + d = 0。
     */
    public static IParticleComponent createKillPlane(ParticleLifetimeKillPlane def) {
        return new IParticleComponent() {
            private final float[] px = def.getPlaneX();
            private final float[] py = def.getPlaneY();
            private final float[] pz = def.getPlaneZ();

            @Override
            public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment molang) {
                float x = buf.getPosX(index);
                float y = buf.getPosY(index);
                float z = buf.getPosZ(index);

                // 检查三个平面方向上的符号变化（从正到负 = 穿过平面）
                if (px.length >= 4) {
                    float val = px[0] * x + px[1] * y + px[2] * z + px[3];
                    if (val <= 0) {
                        buf.markDead(index);
                        return;
                    }
                }
                if (py.length >= 4) {
                    float val = py[0] * x + py[1] * y + py[2] * z + py[3];
                    if (val <= 0) {
                        buf.markDead(index);
                        return;
                    }
                }
                if (pz.length >= 4) {
                    float val = pz[0] * x + pz[1] * y + pz[2] * z + pz[3];
                    if (val <= 0) {
                        buf.markDead(index);
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
            private final List<String> blockIds = def.getBlocks();

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
            private final List<String> blockIds = def.getBlocks();

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
     * 解析十六进制颜色字符串为 RGB float 数组 [0, 1]。
     * 支持 #RGB、#RRGGBB 和 #RRGGBBAA 格式。
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
                    // #RRGGBBAA
                    rgb = (int) Long.parseLong(clean.substring(0, 6), 16);
                    alpha = Integer.parseInt(clean.substring(6, 8), 16) / 255f;
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
     * 在渐变色标列表中进行线性插值，返回指定进度 t ∈ [0, 1] 处的颜色。
     */
    private static float[] interpolateColorStops(List<ParticleAppearanceTinting.ColorStop> stops, float t) {
        if (stops == null || stops.isEmpty()) return new float[]{1, 1, 1, 1};
        if (stops.size() == 1) return parseHexColor(stops.get(0).getColor());
        if (t <= 0) return parseHexColor(stops.get(0).getColor());
        if (t >= 1) return parseHexColor(stops.get(stops.size() - 1).getColor());

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
