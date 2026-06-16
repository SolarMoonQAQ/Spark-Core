package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.IParticleEffectPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * 服务端粒子效果播放器（stub）。
 * 当前为无操作实现，未来会发送网络包到客户端。
 */
public class ServerParticleEffectPlayer implements IParticleEffectPlayer {

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position) {
        return null;
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Quaternionf rotation) {
        return null;
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Quaternionf rotation, Vec3 scale) {
        return null;
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           String locator, UUID entityId) {
        return null;
    }

    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        // TODO: 发送 ParticleEffectStopPacket
    }
}
