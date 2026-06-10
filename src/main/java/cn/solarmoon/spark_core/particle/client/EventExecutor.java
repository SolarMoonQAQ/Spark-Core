package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 事件执行引擎。处理 6 种类型的事件节点。
 */
public class EventExecutor {

    private static final Random RANDOM = new Random();

    private final ParticleMolangEnvironment molang;
    private final ParticleEmitterInstance emitter;
    private final Level level;

    public EventExecutor(ParticleMolangEnvironment molang, ParticleEmitterInstance emitter, Level level) {
        this.molang = molang;
        this.emitter = emitter;
        this.level = level;
    }

    /**
     * 执行指定的事件列表。
     */
    public void execute(List<EventNode> events, ParticleArray buf, int particleIndex) {
        if (events == null || events.isEmpty()) return;
        for (EventNode event : events) {
            execute(event, buf, particleIndex);
        }
    }

    /**
     * 执行单个事件节点。
     */
    public void execute(EventNode event, ParticleArray buf, int particleIndex) {
        if (event == null) return;

        switch (event.getType()) {
            case PARTICLE_EFFECT -> {
                EventNode.ParticleEffectEvent e = (EventNode.ParticleEffectEvent) event;
                // TODO: 触发子粒子效果
                // preEffectExpression 求值后创建子发射器
                if (e.getEffect() != null && !e.getEffect().isEmpty()) {
                    // ParticleEffects.burst(level, ResourceLocation.parse(e.getEffect()), ...);
                }
            }
            case SOUND_EFFECT -> {
                EventNode.SoundEffectEvent e = (EventNode.SoundEffectEvent) event;
                // TODO: 播放音效
                // 需要音效标识符
            }
            case EXPRESSION -> {
                EventNode.ExpressionEvent e = (EventNode.ExpressionEvent) event;
                if (e.getExpression() != null && !e.getExpression().isEmpty()) {
                    MolangExpression expr = molang.compile(e.getExpression());
                    molang.evaluate(expr);
                }
            }
            case SEQUENCE -> {
                EventNode.SequenceEvent e = (EventNode.SequenceEvent) event;
                for (EventNode sub : e.getSequence()) {
                    execute(sub, buf, particleIndex);
                }
            }
            case RANDOMIZE -> {
                EventNode.RandomizeEvent e = (EventNode.RandomizeEvent) event;
                float totalWeight = 0;
                for (float w : e.getWeights()) totalWeight += w;
                if (totalWeight > 0) {
                    float roll = RANDOM.nextFloat() * totalWeight;
                    float cumulative = 0;
                    for (int i = 0; i < e.getEvents().size(); i++) {
                        cumulative += e.getWeights().get(i);
                        if (roll < cumulative) {
                            execute(e.getEvents().get(i), buf, particleIndex);
                            break;
                        }
                    }
                }
            }
            case LOG -> {
                EventNode.LogEvent e = (EventNode.LogEvent) event;
                System.out.println("[ParticleEvent] " + e.getMessage());
            }
        }
    }

    /**
     * 触发发射器过期事件。
     */
    public void fireExpirationEvents(int particleIndex, ParticleArray buf) {
        // 查找事件定义中的 "on_expire" 事件
        Map<String, List<EventNode>> events = emitter.getDefinition().getEvents();
        if (events != null) {
            List<EventNode> onExpire = events.get("on_expire");
            if (onExpire != null) execute(onExpire, buf, particleIndex);
        }
    }
}
