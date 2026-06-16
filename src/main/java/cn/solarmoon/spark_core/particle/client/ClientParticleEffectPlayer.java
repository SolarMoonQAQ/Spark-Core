package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.particle.common.IParticleEffectPlayer;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * 客户端粒子效果播放器（实际实现）。
 */
@OnlyIn(Dist.CLIENT)
public class ClientParticleEffectPlayer implements IParticleEffectPlayer {

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position) {
        return playEffect(level, effectId, position, new Quaternionf(), new Vec3(1, 1, 1));
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Quaternionf rotation) {
        ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) {
            SparkCore.LOGGER.warn("[粒子] 未找到定义: {}，可用定义数: {}",
                    effectId, ParticleDefinitionLoader.getInstance().getAllDefinitions().size());
            return null;
        }

        ParticleEmitterInstance emitter = new ParticleEmitterInstance(def, level);
        emitter.setPosition(position);
        emitter.setRotation(rotation);
        ParticleEmitterManager.getInstance().add(emitter);
        return emitter.getInstanceId();
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Quaternionf rotation, Vec3 scale) {
        ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) {
            SparkCore.LOGGER.warn("[粒子] 未找到定义: {}，可用定义数: {}",
                    effectId, ParticleDefinitionLoader.getInstance().getAllDefinitions().size());
            return null;
        }

        ParticleEmitterInstance emitter = new ParticleEmitterInstance(def, level);
        emitter.setPosition(position);
        emitter.setRotation(rotation);
        emitter.setScale(scale);
        ParticleEmitterManager.getInstance().add(emitter);
        return emitter.getInstanceId();
    }

    @Override
    public UUID playEffect(Level level, ResourceLocation effectId,
                           String locator, UUID entityId) {
        // TODO: 通过 locator 获取实体变换并触发粒子效果
        // 需要从 level 查找 entityId 对应的实体
        ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) {
            SparkCore.LOGGER.warn("[粒子] 未找到定义: {} (locator模式)", effectId);
            return null;
        }

        ParticleEmitterInstance emitter = new ParticleEmitterInstance(def, level);
        emitter.setPosition(Vec3.ZERO);
        emitter.setBindToActor(true);
        ParticleEmitterManager.getInstance().add(emitter);
        return emitter.getInstanceId();
    }

    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        ParticleEmitterManager.getInstance().remove(effectInstanceId);
    }
}
