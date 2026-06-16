package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.SparkCore;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 粒子发射器管理器。管理所有活跃的发射器实例。
 * <p>
 * 线程安全:
 * - add() 使用 ConcurrentLinkedQueue 暂存新发射器
 * - tick() 在主线程消费入队
 */
public class ParticleEmitterManager {

    private static final ParticleEmitterManager INSTANCE = new ParticleEmitterManager();

    private final Map<UUID, ParticleEmitterInstance> emitters = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ParticleEmitterInstance> pendingAdd = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UUID> pendingRemove = new ConcurrentLinkedQueue<>();

    private ParticleEmitterManager() {}

    public static ParticleEmitterManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加发射器（线程安全）。
     *
     * @return 发射器实例 UUID，可用于后续 {@link #remove}
     */
    public UUID add(ParticleEmitterInstance emitter) {
        pendingAdd.offer(emitter);
        return emitter.getInstanceId();
    }

    /**
     * 移除发射器（线程安全）。
     */
    public void remove(UUID instanceId) {
        pendingRemove.offer(instanceId);
    }

    /**
     * 获取所有活跃发射器（仅用于渲染）。
     */
    public List<ParticleEmitterInstance> getActiveEmitters() {
        return emitters.values().stream()
                .filter(ParticleEmitterInstance::isActive)
                .collect(Collectors.toList());
    }

    /**
     * 主线程 tick：消费入队/出队，更新所有发射器。
     */
    public void tick(Level level, float tickDt) {
        // 消费待添加
        ParticleEmitterInstance pending;
        while ((pending = pendingAdd.poll()) != null) {
            emitters.put(pending.getInstanceId(), pending);
        }

        // 消费待移除
        UUID removeId;
        while ((removeId = pendingRemove.poll()) != null) {
            emitters.remove(removeId);
        }

        // tick 所有发射器
        for (ParticleEmitterInstance emitter : emitters.values()) {
            if (!emitter.isExpired()) {
                emitter.tick(level, tickDt);
            }
        }

        // 移除已过期的发射器
        emitters.values().removeIf(ParticleEmitterInstance::isExpired);
    }

    /**
     * 获取当前发射器数量。
     */
    public int getEmitterCount() {
        return emitters.size();
    }

    /**
     * 清空所有发射器。
     */
    public void clear() {
        emitters.clear();
        pendingAdd.clear();
        pendingRemove.clear();
    }
}
