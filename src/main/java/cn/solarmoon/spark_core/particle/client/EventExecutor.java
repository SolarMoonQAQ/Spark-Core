package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.ParticleEmitterInstance;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 事件执行引擎。处理 6 种类型的事件节点。
 * <p>
 * 支持按事件名称字符串从定义中查找并执行事件列表。
 * 事件名称对应 JSON 中 particle_effect.events 的键。
 */
public class EventExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventExecutor.class);
    private static final Random RANDOM = new Random();

    private final ParticleMolangEnvironment molang;
    private final ParticleEmitterInstance emitter;
    private final Level level;

    public EventExecutor(ParticleMolangEnvironment molang, ParticleEmitterInstance emitter, Level level) {
        this.molang = molang;
        this.emitter = emitter;
        this.level = level;
    }

    // ==================== 按事件名称执行 ====================

    /**
     * 按事件名称列表执行对应的事件。
     */
    public void fireEventNames(List<String> eventNames) {
        if (eventNames == null || eventNames.isEmpty()) return;
        Map<String, List<EventNode>> allEvents = emitter.getDefinition().getEvents();
        if (allEvents == null || allEvents.isEmpty()) return;
        for (String name : eventNames) {
            List<EventNode> nodes = allEvents.get(name);
            if (nodes == null) continue;
            execute(nodes, null, -1);
        }
    }

    /**
     * 按事件名称列表执行对应的事件（粒子上下文）。
     */
    public void fireEventNames(List<String> eventNames, ParticleArray buf, int particleIndex) {
        if (eventNames == null || eventNames.isEmpty()) return;
        Map<String, List<EventNode>> allEvents = emitter.getDefinition().getEvents();
        if (allEvents == null || allEvents.isEmpty()) return;
        for (String name : eventNames) {
            List<EventNode> nodes = allEvents.get(name);
            if (nodes == null) continue;
            execute(nodes, buf, particleIndex);
        }
    }

    // ==================== 执行事件列表 ====================

    /**
     * 执行指定的事件列表（无粒子上下文，用于发射器级事件）。
     */
    public void execute(List<EventNode> events) {
        if (events == null || events.isEmpty()) return;
        for (EventNode event : events) {
            execute(event, null, -1);
        }
    }

    /**
     * 执行指定的事件列表（粒子上下文）。
     */
    public void execute(List<EventNode> events, ParticleArray buf, int particleIndex) {
        if (events == null || events.isEmpty()) return;
        for (EventNode event : events) {
            execute(event, buf, particleIndex);
        }
    }

    /**
     * 执行单个事件节点。
     *
     * @param event         事件节点
     * @param buf           粒子数组（发射器级事件可传 null）
     * @param particleIndex 粒子索引（发射器级事件传 -1）
     */
    public void execute(EventNode event, @Nullable ParticleArray buf, int particleIndex) {
        if (event == null) return;

        switch (event.getType()) {
            case PARTICLE_EFFECT -> executeParticleEffect((EventNode.ParticleEffectEvent) event, buf, particleIndex);
            case SOUND_EFFECT -> executeSoundEffect((EventNode.SoundEffectEvent) event, buf, particleIndex);
            case EXPRESSION -> executeExpression((EventNode.ExpressionEvent) event);
            case SEQUENCE -> executeSequence((EventNode.SequenceEvent) event, buf, particleIndex);
            case RANDOMIZE -> executeRandomize((EventNode.RandomizeEvent) event, buf, particleIndex);
            case LOG -> executeLog((EventNode.LogEvent) event);
        }
    }

    // ==================== 各事件类型执行逻辑 ====================

    /**
     * 执行粒子效果事件。根据 type 决定子发射器的创建位置和行为。
     * <ul>
     *   <li>EMITTER — 在发射器位置创建，独立存在</li>
     *   <li>EMITTER_BOUND — 在发射器位置创建，跟随发射器</li>
     *   <li>PARTICLE — 在粒子位置创建，独立存在（原默认行为）</li>
     *   <li>PARTICLE_WITH_VELOCITY — 在粒子位置创建并继承粒子速度</li>
     * </ul>
     */
    private void executeParticleEffect(EventNode.ParticleEffectEvent e, @Nullable ParticleArray buf, int particleIndex) {
        // 执行前置 Molang 表达式（pre_effect_expression），使用预编译表达式
        if (e.getCompiledPreEffect() != null) {
            try {
                molang.evaluate(e.getCompiledPreEffect());
            } catch (Exception ex) {
                LOGGER.warn("粒子效果事件 pre_effect_expression 执行失败: {}", e.getPreEffectExpression(), ex);
            }
        } else if (e.getPreEffectExpression() != null && !e.getPreEffectExpression().isEmpty()) {
            // 无预编译时回退到运行时编译
            try {
                MolangExpression expr = molang.compile(e.getPreEffectExpression());
                molang.evaluate(expr);
            } catch (Exception ex) {
                LOGGER.warn("粒子效果事件 pre_effect_expression 编译失败: {}", e.getPreEffectExpression(), ex);
            }
        }

        String effectStr = e.getEffect();
        if (effectStr == null || effectStr.isEmpty() || level == null) return;
        ResourceLocation effectId = ResourceLocation.parse(effectStr);
        ParticleEffectDefinition childDef = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (childDef == null) {
            LOGGER.debug("粒子效果事件引用的效果不存在: {}", effectStr);
            return;
        }

        Vec3 pos;
        switch (e.getParticleEffectType()) {
            case EMITTER, EMITTER_BOUND -> {
                // 在发射器当前位置创建
                pos = emitter.getPosition();
            }
            case PARTICLE, PARTICLE_WITH_VELOCITY -> {
                // 在当前粒子位置创建
                if (buf == null || particleIndex < 0) return;
                pos = new Vec3(buf.getPosX(particleIndex), buf.getPosY(particleIndex), buf.getPosZ(particleIndex));
            }
            default -> {
                return;
            }
        }

        ParticleEmitterInstance child = new ParticleEmitterInstance(childDef, level);
        child.setPosition(pos);

        // PARTICLE_WITH_VELOCITY：继承粒子速度
        if (e.getParticleEffectType() == EventNode.ParticleEffectType.PARTICLE_WITH_VELOCITY && buf != null && particleIndex >= 0) {
            child.setVelocity(new Vec3(buf.getVelX(particleIndex), buf.getVelY(particleIndex), buf.getVelZ(particleIndex)));
        }

        // EMITTER_BOUND：子发射器标记为绑定到发射器（每 tick 跟随位置）
        if (e.getParticleEffectType() == EventNode.ParticleEffectType.EMITTER_BOUND) {
            child.setBindToActor(true);
        }

        ParticleEmitterManager.getInstance().add(child);
    }

    /**
     * 执行音效事件。在粒子位置播放指定音效。
     */
    private void executeSoundEffect(EventNode.SoundEffectEvent e, @Nullable ParticleArray buf, int particleIndex) {
        String soundStr = e.getSound();
        if (soundStr == null || soundStr.isEmpty()) return;

        // 确定播放位置：有粒子上下文则用粒子位置，否则用发射器位置
        Vec3 pos;
        if (buf != null && particleIndex >= 0) {
            pos = new Vec3(buf.getPosX(particleIndex), buf.getPosY(particleIndex), buf.getPosZ(particleIndex));
        } else {
            pos = emitter.getPosition();
        }

        if (level.isClientSide()) {
            try {
                ResourceLocation soundId = ResourceLocation.parse(soundStr);
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
                level.playLocalSound(
                        pos.x, pos.y, pos.z,
                        soundEvent, SoundSource.AMBIENT,
                        1.0f, 1.0f, false
                );
            } catch (Exception ex) {
                LOGGER.warn("粒子音效事件播放失败: {}", soundStr, ex);
            }
        }
    }

    /**
     * 执行 Molang 表达式事件，使用预编译表达式。
     */
    private void executeExpression(EventNode.ExpressionEvent e) {
        if (e.getCompiledExpression() != null) {
            try {
                molang.evaluate(e.getCompiledExpression());
            } catch (Exception ex) {
                LOGGER.warn("粒子表达式事件执行失败: {}", e.getExpression(), ex);
            }
        } else if (e.getExpression() != null && !e.getExpression().isEmpty()) {
            try {
                MolangExpression expr = molang.compile(e.getExpression());
                molang.evaluate(expr);
            } catch (Exception ex) {
                LOGGER.warn("粒子表达式事件编译失败: {}", e.getExpression(), ex);
            }
        }
    }

    /**
     * 执行顺序事件。
     */
    private void executeSequence(EventNode.SequenceEvent e, @Nullable ParticleArray buf, int particleIndex) {
        for (EventNode sub : e.getSequence()) {
            execute(sub, buf, particleIndex);
        }
    }

    /**
     * 执行随机事件。
     */
    private void executeRandomize(EventNode.RandomizeEvent e, @Nullable ParticleArray buf, int particleIndex) {
        float totalWeight = 0;
        for (float w : e.getWeights()) totalWeight += w;
        if (totalWeight <= 0) return;

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

    /**
     * 执行日志事件（调试用途）。
     */
    private void executeLog(EventNode.LogEvent e) {
        LOGGER.info("[粒子事件] {}", e.getMessage());
    }

    // ==================== 生命周期快捷方法 ====================

    /**
     * 触发粒子过期事件（查找定义中 "on_expire" 事件）。
     */
    public void fireExpirationEvents(int particleIndex, ParticleArray buf) {
        Map<String, List<EventNode>> events = emitter.getDefinition().getEvents();
        if (events == null || events.isEmpty()) return;
        List<EventNode> onExpire = events.get("on_expire");
        if (onExpire != null) execute(onExpire, buf, particleIndex);
    }

    /**
     * 触发发射器创建事件。
     */
    public void fireEmitterCreationEvents() {
        // 由 EmitterLifetimeEvents 运行时组件触发
    }

    /**
     * 触发发射器过期事件（查找定义中 "emitter_expire" 事件）。
     */
    public void fireEmitterExpirationEvents() {
        Map<String, List<EventNode>> events = emitter.getDefinition().getEvents();
        if (events == null || events.isEmpty()) return;
        List<EventNode> onExpire = events.get("emitter_expire");
        if (onExpire != null) execute(onExpire);
    }

    /**
     * 按发射器年龄触发时间线事件。
     * 遍历 events 中形如 "0.5"、"1.0" 等数字键的时间线事件。
     *
     * @param emitterAge 发射器当前年龄（秒）
     * @param lastIndex  上次触发到的索引（用于增量触发）
     * @return 更新后的 lastIndex
     */
    public int fireEmitterTimelineEvents(float emitterAge, int lastIndex) {
        return fireTimelineEvents(emitterAge, lastIndex, null, -1);
    }

    /**
     * 按粒子年龄触发时间线事件。
     *
     * @param particleAge  粒子当前年龄（秒）
     * @param lastIndex    上次触发到的索引
     * @param buf          粒子数组
     * @param particleIndex 粒子索引
     * @return 更新后的 lastIndex
     */
    public int fireParticleTimelineEvents(float particleAge, int lastIndex, ParticleArray buf, int particleIndex) {
        return fireTimelineEvents(particleAge, lastIndex, buf, particleIndex);
    }

    /**
     * 通用时间线事件触发逻辑。
     * 遍历定义中所有以数字为键的事件，当 age 超过键值时触发。
     */
    private int fireTimelineEvents(float age, int lastIndex, @Nullable ParticleArray buf, int particleIndex) {
        Map<String, List<EventNode>> events = emitter.getDefinition().getEvents();
        if (events == null || events.isEmpty()) return lastIndex;

        // 收集所有数字键并排序
        List<Float> times = events.keySet().stream()
                .filter(k -> { try { Float.parseFloat(k); return true; } catch (NumberFormatException e) { return false; } })
                .map(Float::parseFloat)
                .sorted()
                .toList();

        int idx = 0;
        for (float time : times) {
            if (idx < lastIndex) { idx++; continue; }
            if (age >= time) {
                List<EventNode> nodes = events.get(String.valueOf(time));
                if (nodes != null) {
                    if (buf != null && particleIndex >= 0) {
                        execute(nodes, buf, particleIndex);
                    } else {
                        execute(nodes);
                    }
                }
                lastIndex = idx + 1;
            }
            idx++;
        }
        return lastIndex;
    }
}
