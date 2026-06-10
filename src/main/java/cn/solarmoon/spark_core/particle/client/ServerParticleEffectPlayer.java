package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.IParticleEffectPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 服务端粒子效果播放器（stub）。
 * 当前为无操作实现，未来会发送网络包到客户端。
 */
public class ServerParticleEffectPlayer implements IParticleEffectPlayer {

    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Vec3 rotation) {
        // TODO: 发送 ParticleEffectTriggerPacket 给附近玩家
        // PacketDistributor.sendToPlayersNear(...)
    }

    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           String locator, UUID entityId) {
        // TODO: 客户端侧将 locator 变换求值后渲染
    }

    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        // TODO: 发送 ParticleEffectStopPacket
    }
}
