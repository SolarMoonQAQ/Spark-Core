package cn.solarmoon.spark_core.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 球面声学模型声源
 *
 * <p>以收听者（摄像机）为球心生成一个半径为 {@link #DEFAULT_RADIUS} 的球，
 * 发声点始终位于球面上：任意调用本模型的物体，其与收听者位置的连线与球面的交点
 * 即为实时变化的发声位置。物体与摄像机相对移动时，发声点会随之在球面上滑动。</p>
 *
 * <p>物理特性：</p>
 * <ul>
 *   <li><b>多普勒效应</b>：使用物体真实运动速度计算音调变化（非球面投影点的切向速度，
 *       投影点的径向速度恒为零，不会产生多普勒效应）</li>
 *   <li><b>大气衰减</b>：振幅按 {@code A(d) = e^(-α·d) · (R/d)²} 衰减，
 *       其中 d 为物体到收听者的真实距离，R 为球面半径，α 为大气吸收常数</li>
 * </ul>
 *
 * <p>使用方式：将本模型包装在普通动态声源或实体外，再通过
 * {@link ISoundSpreader#playSpreadingSound} 等入口播放。</p>
 *
 * <pre>{@code
 * // 包装已有动态声源
 * SphereSoundModel model = SphereSoundModel.of(mySoundSpreader);
 * model.playSpreadingSound(level, soundEvent, soundType);
 *
 * // 包装实体
 * SphereSoundModel entityModel = SphereSoundModel.of(entity);
 * entityModel.playSpreadingSound(level, soundEvent, soundType);
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public class SphereSoundModel implements ISoundSpreader {

    /**
     * 默认球面半径（方块）
     */
    public static final double DEFAULT_RADIUS = 10.0;

    /**
     * 默认大气吸收常数 α（单位：1/方块）
     * <p>对应衰减因子 exp(-α·d)：在 Minecraft 尺度下，d=100 时衰减约 0.90，
     * d=1000 时衰减约 0.37，配合 (R/d)² 获得渐进的远距离静默。</p>
     */
    public static final double DEFAULT_ABSORPTION = 0.001;

    private final ISoundSpreader delegate;
    private final double radius;
    private final double absorption;

    public SphereSoundModel(ISoundSpreader delegate) {
        this(delegate, DEFAULT_RADIUS, DEFAULT_ABSORPTION);
    }

    public SphereSoundModel(ISoundSpreader delegate, double radius, double absorption) {
        this.delegate = delegate;
        this.radius = radius;
        this.absorption = absorption;
    }

    /**
     * 包装一个已有的动态声源，使用默认半径与大气吸收常数
     */
    public static SphereSoundModel of(ISoundSpreader delegate) {
        return new SphereSoundModel(delegate);
    }

    /**
     * 包装一个实体作为声源（位置取眼部，速度取实体实际移动速度，单位方块/秒）
     */
    public static SphereSoundModel of(Entity entity) {
        return new SphereSoundModel(new ISoundSpreader() {
            @Override
            public Vec3 getPosition(UUID uuid, SoundEvent event) {
                return entity.getEyePosition();
            }

            @Override
            public Vec3 getSpeed(UUID uuid, SoundEvent event) {
                // getDeltaMovement 单位每 tick，转换为方块/秒，与接口约定一致
                return entity.getDeltaMovement().scale(20.0);
            }
        });
    }

    /**
     * 包装一组位置/速度提供者（用于无实体的机械声源等）
     *
     * @param positionSupplier 提供物体真实位置，单位：方块
     * @param speedSupplier    提供物体真实速度，单位：方块/秒
     */
    public static SphereSoundModel of(Supplier<Vec3> positionSupplier, Supplier<Vec3> speedSupplier) {
        return new SphereSoundModel(new ISoundSpreader() {
            @Override
            public Vec3 getPosition(UUID uuid, SoundEvent event) {
                return positionSupplier.get();
            }

            @Override
            public Vec3 getSpeed(UUID uuid, SoundEvent event) {
                return speedSupplier.get();
            }
        });
    }

    /**
     * 计算物体位置在球面上的投影点：
     * 摄像机位置 + 归一化(物体位置 - 摄像机位置) × 半径
     */
    private Vec3 projectToSphere(Vec3 cameraPos, Vec3 objPos) {
        Vec3 direction = objPos.subtract(cameraPos);
        double length = direction.length();
        if (length < 1.0E-4) {
            // 物体与摄像机几乎重合：退化为摄像机视线方向，避免归一化除零
            Vec3 fallback = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
            if (fallback.lengthSqr() < 1.0E-8) {
                fallback = new Vec3(1, 0, 0);
            }
            direction = fallback.normalize();
        } else {
            direction = direction.scale(1.0 / length);
        }
        return cameraPos.add(direction.scale(radius));
    }

    @Override
    public Vec3 getPosition(UUID uuid, SoundEvent event) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return projectToSphere(cameraPos, delegate.getPosition(uuid, event));
    }

    @Override
    public Vec3 getSpeed(UUID uuid, SoundEvent event) {
        // 多普勒速度 = 物体真实速度（供 SoundEngineMixin 计算音调）
        return delegate.getSpeed(uuid, event);
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        return delegate.getPitch(uuid, event);
    }

    @Override
    public float getVolume(UUID uuid, SoundEvent event) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 objPos = delegate.getPosition(uuid, event);
        double distance = objPos.distanceTo(cameraPos);
        double attenuation;
        if (distance < 1.0E-4) {
            // 物体与摄像机重合时避免除零，取最近距离的衰减
            distance = 1.0E-4;
        }
        // 大气吸收衰减 + 平方反比衰减：A(d) = e^(-α·d) · (R/d)²
        attenuation = Math.exp(-absorption * distance) * (radius / distance) * (radius / distance);
        float volume = (float) (delegate.getVolume(uuid, event) * attenuation);
        return Math.clamp(volume, 0.0f, 1.0f);
    }

    @Override
    public boolean isAttenuationIncluded() {
        return true;
    }

    @Override
    public boolean shouldApplyInteriorEffect(Entity listener, SoundEvent event, boolean isFirstPerson) {
        return delegate.shouldApplyInteriorEffect(listener, event, isFirstPerson);
    }
}
