package cn.solarmoon.spark_core.particle.common.data.event;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 事件节点基类。对应基岩版 6 种事件类型。
 */
public abstract class EventNode {

    /** 事件类型 */
    public abstract EventType getType();

    public enum EventType {
        PARTICLE_EFFECT,
        SOUND_EFFECT,
        SEQUENCE,
        RANDOMIZE,
        EXPRESSION,
        LOG
    }

    /**
     * 粒子效果事件类型，控制子发射器的创建方式和位置。
     */
    public enum ParticleEffectType {
        /** 在发射器位置创建，不绑定 */
        EMITTER,
        /** 在发射器位置创建，跟随发射器移动 */
        EMITTER_BOUND,
        /** 在粒子位置创建，独立存在（当前默认行为） */
        PARTICLE,
        /** 在粒子位置创建并继承粒子速度 */
        PARTICLE_WITH_VELOCITY;

        public static ParticleEffectType fromString(String str) {
            if (str == null) return EMITTER;
            return switch (str.toLowerCase()) {
                case "emitter_bound" -> EMITTER_BOUND;
                case "particle" -> PARTICLE;
                case "particle_with_velocity" -> PARTICLE_WITH_VELOCITY;
                default -> EMITTER;
            };
        }
    }

    /** 粒子效果事件 */
    public static class ParticleEffectEvent extends EventNode {
        private final String effect;              // 粒子标识符
        private final ParticleEffectType type;    // 子发射器类型
        private final String preEffectExpression; // Molang 前置表达式（原始字符串，调试用）
        @Nullable
        private final MolangExpression compiledPreEffect; // 预编译的 pre_effect_expression

        public ParticleEffectEvent(String effect, ParticleEffectType type, String preEffectExpression, @Nullable MolangExpression compiledPreEffect) {
            this.effect = effect;
            this.type = type;
            this.preEffectExpression = preEffectExpression;
            this.compiledPreEffect = compiledPreEffect;
        }

        public String getEffect() { return effect; }
        public ParticleEffectType getParticleEffectType() { return type; }
        public String getPreEffectExpression() { return preEffectExpression; }
        @Nullable public MolangExpression getCompiledPreEffect() { return compiledPreEffect; }

        @Override
        public EventType getType() { return EventType.PARTICLE_EFFECT; }
    }

    /** 音效事件 */
    public static class SoundEffectEvent extends EventNode {
        private final String sound;  // 音效标识符

        public SoundEffectEvent(String sound) { this.sound = sound; }

        public String getSound() { return sound; }

        @Override
        public EventType getType() { return EventType.SOUND_EFFECT; }
    }

    /** 顺序执行事件 */
    public static class SequenceEvent extends EventNode {
        private final List<EventNode> sequence;

        public SequenceEvent(List<EventNode> sequence) { this.sequence = sequence; }

        public List<EventNode> getSequence() { return sequence; }

        @Override
        public EventType getType() { return EventType.SEQUENCE; }
    }

    /** 随机选择事件 */
    public static class RandomizeEvent extends EventNode {
        private final List<EventNode> events;
        private final List<Float> weights;

        public RandomizeEvent(List<EventNode> events, List<Float> weights) {
            this.events = events;
            this.weights = weights;
        }

        public List<EventNode> getEvents() { return events; }
        public List<Float> getWeights() { return weights; }

        @Override
        public EventType getType() { return EventType.RANDOMIZE; }
    }

    /** Molang 表达式事件（预编译） */
    public static class ExpressionEvent extends EventNode {
        private final String expression;
        @Nullable
        private final MolangExpression compiledExpression;

        public ExpressionEvent(String expression, @Nullable MolangExpression compiledExpression) {
            this.expression = expression;
            this.compiledExpression = compiledExpression;
        }

        public String getExpression() { return expression; }
        @Nullable public MolangExpression getCompiledExpression() { return compiledExpression; }

        @Override
        public EventType getType() { return EventType.EXPRESSION; }
    }

    /** 日志事件 */
    public static class LogEvent extends EventNode {
        private final String message;

        public LogEvent(String message) { this.message = message; }

        public String getMessage() { return message; }

        @Override
        public EventType getType() { return EventType.LOG; }
    }
}
