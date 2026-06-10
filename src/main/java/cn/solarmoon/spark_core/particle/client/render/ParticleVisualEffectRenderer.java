package cn.solarmoon.spark_core.particle.client.render;

import cn.solarmoon.spark_core.particle.client.ParticleEmitterInstance;
import cn.solarmoon.spark_core.particle.client.ParticleEmitterManager;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 粒子系统的 VisualEffectRenderer 实现。
 * <p>
 * 继承 {@link VisualEffectRenderer} 以获得 renderStage 调度支持：
 * <ul>
 *   <li>{@link #tick()} — 驱动 {@link ParticleEmitterManager} 的发射器更新</li>
 *   <li>{@link #getRenderStage()} — 返回 {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES}</li>
 *   <li>{@link #render(RenderLevelStageEvent, MultiBufferSource, float)} — 遍历所有活跃发射器渲染 Billboard 粒子</li>
 * </ul>
 */
public class ParticleVisualEffectRenderer extends VisualEffectRenderer {

    private final ParticleRenderer renderer = new ParticleRenderer();

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Minecraft 的固定时间步长：每 tick 0.05 秒
        ParticleEmitterManager.getInstance().tick(mc.level, 0.05f);
    }

    @Override
    public void physTick(PhysicsLevel physLevel) {
        // 粒子系统不参与物理子步，无需实现
    }

    /**
     * 指定渲染阶段为 AFTER_ENTITIES。
     * 粒子是实体级别的特效，应在所有实体渲染完成后、透明粒子/拖尾之前渲染。
     */
    @Nullable
    @Override
    public RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
    }

    /**
     * 在 AFTER_ENTITIES 阶段渲染所有活跃粒子的 Billboard。
     */
    @Override
    public void render(RenderLevelStageEvent event, MultiBufferSource bufferSource, float partialTicks) {
        var pose = event.getPoseStack();
        var camera = event.getCamera();

        // 获取完整光照数据（打包的天空+方块光照）
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int light = LevelRenderer.getLightColor(mc.level, camera.getBlockPosition());
        List<ParticleEmitterInstance> emitters = ParticleEmitterManager.getInstance().getActiveEmitters();

        renderer.renderAll(emitters, pose, bufferSource, camera, partialTicks, light);
    }
}
