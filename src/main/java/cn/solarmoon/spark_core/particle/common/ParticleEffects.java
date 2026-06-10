package cn.solarmoon.spark_core.particle.common;

import cn.solarmoon.spark_core.particle.client.ClientParticleEffectPlayer;
import cn.solarmoon.spark_core.particle.client.ServerParticleEffectPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 粒子效果触发辅助类（双端统一入口）。
 * <p>
 * 与 {@code SpreadingSoundHelper} 相同的工厂模式：
 * 静态字段 INSTANCE 通过 {@code FMLEnvironment.dist.isClient()} 判断环境，
 * 客户端使用 {@code ClientParticleEffectPlayer}，服务端使用 {@code ServerParticleEffectPlayer}（stub）。
 * <p>
 * 当前仅在客户端实际有效，服务端调用静默返回。
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

    /**
     * 在指定位置触发粒子效果，双端可用。
     * 当前仅在客户端实际有效。
     */
    public static void burst(Level level, ResourceLocation effectId,
                             Vec3 position, Vec3 rotation) {
        INSTANCE.playEffect(level, effectId, position, rotation);
    }

    /**
     * 触发粒子效果并绑定到 locator，双端可用。
     */
    public static void burst(Level level, ResourceLocation effectId,
                             String locator, UUID entityId) {
        INSTANCE.playEffect(level, effectId, locator, entityId);
    }

    /**
     * 停止指定粒子效果，双端可用。
     */
    public static void stop(Level level, UUID effectInstanceId) {
        INSTANCE.stopEffect(level, effectInstanceId);
    }
}
