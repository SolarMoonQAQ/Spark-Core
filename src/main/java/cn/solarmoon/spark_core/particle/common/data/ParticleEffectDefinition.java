package cn.solarmoon.spark_core.particle.common.data;

import cn.solarmoon.spark_core.particle.common.data.curve.ParticleCurve;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 粒子效果定义（不可变数据模型）。
 * 对应 JSON 中完整 particle_effect 的解析产物。
 */
public class ParticleEffectDefinition {
    private final ResourceLocation identifier;
    private final ParticleDescription description;
    private final EmitterPreset emitterPreset;
    private final ParticlePreset particlePreset;
    private final Map<String, ParticleCurve> curves;
    private final Map<String, List<EventNode>> events;

    public ParticleEffectDefinition(ResourceLocation identifier, ParticleDescription description,
                                    EmitterPreset emitterPreset, ParticlePreset particlePreset,
                                    Map<String, ParticleCurve> curves,
                                    Map<String, List<EventNode>> events) {
        this.identifier = identifier;
        this.description = description;
        this.emitterPreset = emitterPreset;
        this.particlePreset = particlePreset;
        this.curves = curves;
        this.events = events;
    }

    public ResourceLocation getIdentifier() { return identifier; }
    public ParticleDescription getDescription() { return description; }
    public EmitterPreset getEmitterPreset() { return emitterPreset; }
    public ParticlePreset getParticlePreset() { return particlePreset; }
    public Map<String, ParticleCurve> getCurves() { return curves; }
    public Map<String, List<EventNode>> getEvents() { return events; }
}
