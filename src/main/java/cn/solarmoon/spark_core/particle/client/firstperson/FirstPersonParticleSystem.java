package cn.solarmoon.spark_core.particle.client.firstperson;

import cn.solarmoon.spark_core.particle.common.ParticleEmitterInstance;
import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第一人称粒子系统。
 * <p>
 * 管理在第一人称视角下可见的粒子效果。
 * 这些粒子使用摄像机空间坐标系，不跟随玩家身体旋转。
 * 适用于枪械开火、武器特效等第一人称效果。
 */
public class FirstPersonParticleSystem {

    private static final FirstPersonParticleSystem INSTANCE = new FirstPersonParticleSystem();

    private final Map<UUID, FirstPersonEmitter> emitters = new ConcurrentHashMap<>();

    private FirstPersonParticleSystem() {}

    public static FirstPersonParticleSystem getInstance() {
        return INSTANCE;
    }

    /**
     * 在第一人称视角触发粒子效果。
     *
     * @param effectId 粒子效果标识符
     * @param position 相对于摄像机的位置
     * @param rotation 旋转
     */
    public void playEffect(ResourceLocation effectId, Vec3 position, Vec3 rotation) {
        var def = cn.solarmoon.spark_core.particle.client.ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) return;

        FirstPersonEmitter fpEmitter = new FirstPersonEmitter(def, position, rotation);
        emitters.put(fpEmitter.getInstanceId(), fpEmitter);
    }

    /**
     * 停止第一人称粒子效果。
     */
    public void stopEffect(UUID instanceId) {
        emitters.remove(instanceId);
    }

    /**
     * Tick 所有第一人称发射器。
     */
    public void tick(float tickDt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        emitters.values().removeIf(FirstPersonEmitter::isExpired);

        for (FirstPersonEmitter emitter : emitters.values()) {
            emitter.tick(mc.level, tickDt);
        }
    }

    /**
     * 渲染所有第一人称粒子。
     */
    public void render(com.mojang.blaze3d.vertex.PoseStack pose,
                       net.minecraft.client.renderer.MultiBufferSource buffer,
                       Camera camera, float partialTick, int light) {
        // 使用摄像机空间的变换矩阵
        // 粒子位置相对于摄像机
        for (FirstPersonEmitter emitter : emitters.values()) {
            if (emitter.isExpired()) continue;
            var renderer = new cn.solarmoon.spark_core.particle.client.render.ParticleRenderer();
            renderer.renderEmitter(emitter, pose, buffer, camera, partialTick, light);
        }
    }

    /**
     * 第一人称发射器包装器。
     * 将粒子位置偏移到摄像机空间。
     */
    private static class FirstPersonEmitter extends ParticleEmitterInstance {
        private final Vec3 fpPosition;

        public FirstPersonEmitter(ParticleEffectDefinition def, Vec3 position, Vec3 rotation) {
            super(def, null);
            this.fpPosition = position;
            setPosition(position);
            setRotation(new Quaternionf().rotationXYZ(
                    (float) rotation.x, (float) rotation.y, (float) rotation.z));
        }

        @Override
        public void tick(net.minecraft.world.level.Level level, float tickDt) {
            // 更新摄像机相对位置
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
                setPosition(camPos.add(fpPosition));
            }
            // TODO: 当 emitter_local_space.velocity=true 时，设置发射器速度为摄像机运动速度
            //   setVelocity(new Vec3(cameraVx, cameraVy, cameraVz));
            super.tick(level, tickDt);
        }
    }
}
