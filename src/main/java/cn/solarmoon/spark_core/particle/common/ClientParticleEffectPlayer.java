package cn.solarmoon.spark_core.particle.common;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.particle.client.ParticleDefinitionLoader;
import cn.solarmoon.spark_core.particle.client.ParticleEmitterManager;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import net.minecraft.resources.ResourceLocation;
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
                           IParticleAnchor anchor, String locatorName) {
        ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) {
            SparkCore.LOGGER.warn("[粒子] 未找到定义: {} (锚点模式, locator={})",
                    effectId, locatorName);
            return null;
        }

        ParticleEmitterInstance emitter = new ParticleEmitterInstance(def, level);
        emitter.bindToAnchor(anchor, locatorName);
            // 初始位姿从锚点获取一次
            com.jme3.math.Transform init = anchor.getLocatorTransform(emitter.getInstanceId(), locatorName);
            if (init != null) {
                emitter.setPosition(new Vec3(init.getTranslation().x,
                    init.getTranslation().y, init.getTranslation().z));
                // JME Quaternion → JOML Quaternionf
                emitter.setRotation(new org.joml.Quaternionf(
                    init.getRotation().getX(), init.getRotation().getY(),
                    init.getRotation().getZ(), init.getRotation().getW()));
                emitter.setScale(new Vec3(init.getScale().x,
                    init.getScale().y, init.getScale().z));
            }
        ParticleEmitterManager.getInstance().add(emitter);
        return emitter.getInstanceId();
    }

    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        ParticleEmitterManager.getInstance().remove(effectInstanceId);
    }

    @Override
    public void setTexture(Level level, UUID effectInstanceId, ResourceLocation texture) {
        ParticleEmitterManager.getInstance().setTexture(effectInstanceId, texture);
    }
}
