package cn.solarmoon.spark_core.api;

import cn.solarmoon.spark_core.particle.common.ClientParticleEffectPlayer;
import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import cn.solarmoon.spark_core.particle.common.ServerParticleEffectPlayer;
import cn.solarmoon.spark_core.particle.common.IParticleEffectPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 粒子效果触发辅助类（双端统一入口）。
 * <p>
 * 与 {@code SpreadingSoundHelper} 相同的工厂模式：
 * 静态字段 INSTANCE 通过 {@code FMLEnvironment.dist.isClient()} 判断环境，
 * 客户端使用 {@code ClientParticleEffectPlayer}，服务端使用 {@code ServerParticleEffectPlayer}（stub）。
 * <p>
 * 返回的 UUID 可用于后续 {@link #stop} 停止。
 * <p>
 * 注意：使用 Supplier + 方法引用而非直接 new，因为方法引用基于 invokedynamic，
 * 只有在分支实际执行时才会解析类引用，避免服务端加载客户端类的类加载问题。
 */
public class ParticleEffects {

    private static final IParticleEffectPlayer INSTANCE = init();

    private static IParticleEffectPlayer init() {
        Supplier<IParticleEffectPlayer> player;
        if (FMLEnvironment.dist.isClient()) {
            player = ClientParticleEffectPlayer::new;
        } else {
            player = ServerParticleEffectPlayer::new;
        }
        return player.get();
    }

    /** 仅指定位置触发粒子效果。 */
    public static UUID burst(Level level, ResourceLocation effectId,
                             Vec3 position) {
        return INSTANCE.playEffect(level, effectId, position);
    }

    /** 指定位置和旋转（四元数）触发粒子效果。 */
    public static UUID burst(Level level, ResourceLocation effectId,
                             Vec3 position, Quaternionf rotation) {
        return INSTANCE.playEffect(level, effectId, position, rotation);
    }

    /** 指定完整变换（位置、旋转、非均匀缩放）触发粒子效果。 */
    public static UUID burst(Level level, ResourceLocation effectId,
                             Vec3 position, Quaternionf rotation, Vec3 scale) {
        return INSTANCE.playEffect(level, effectId, position, rotation, scale);
    }

    /**
     * 触发持久粒子效果并绑定到定位器锚点。
     * 发射器每 tick 轮询锚点 locator 位姿以跟随移动（如炮口烟雾）。
     *
     * @param level       维度
     * @param effectId    粒子效果标识符
     * @param anchor      定位器锚点
     * @param locatorName 定位器名称
     * @return 效果实例 UUID，可用于 {@link #stop}
     */
    public static UUID burstPersistent(Level level, ResourceLocation effectId,
                                        IParticleAnchor anchor, String locatorName) {
        return INSTANCE.playEffect(level, effectId, anchor, locatorName);
    }

    /**
     * 停止指定粒子效果，双端可用。
     */
    public static void stop(Level level, UUID effectInstanceId) {
        INSTANCE.stopEffect(level, effectInstanceId);
    }

    /**
     * 动态修改指定发射器实例的运行时贴图，双端可用。
     * 在发射器存活期间可多次调用切换贴图，下一帧生效。
     */
    public static void setTexture(Level level, UUID effectInstanceId, ResourceLocation texture) {
        INSTANCE.setTexture(level, effectInstanceId, texture);
    }
}
