package cn.solarmoon.spark_core.particle.common.data.event;

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

    /** 粒子效果事件 */
    public static class ParticleEffectEvent extends EventNode {
        private final String effect;              // 粒子标识符
        private final String preEffectExpression; // Molang 前置表达式

        public ParticleEffectEvent(String effect, String preEffectExpression) {
            this.effect = effect;
            this.preEffectExpression = preEffectExpression;
        }

        public String getEffect() { return effect; }
        public String getPreEffectExpression() { return preEffectExpression; }

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

    /** Molang 表达式事件 */
    public static class ExpressionEvent extends EventNode {
        private final String expression;

        public ExpressionEvent(String expression) { this.expression = expression; }

        public String getExpression() { return expression; }

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
