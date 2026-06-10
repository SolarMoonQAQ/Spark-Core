package cn.solarmoon.spark_core.particle.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 粒子效果引用。封装了触发粒子效果所需的全部信息。
 */
public record ParticleEffectReference(
        ResourceLocation effectId,
        Vec3 position,
        Vec3 rotation
) {}
