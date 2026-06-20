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
     * 指定渲染阶段为 AFTER_PARTICLES。
     */
    @Nullable
    @Override
    public RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_PARTICLES;
    }

    /**
     * 在 AFTER_ENTITIES 阶段渲染所有活跃粒子的 Billboard。
     * <p>
     * AFTER_ENTITIES 阶段的 PoseStack 不含摄像机平移，仅含摄像机旋转。
     * 因此需手动 translate(-camPos) 将渲染偏移到摄像机相对空间，
     * 与 {@code PartAssemblyRenderer} 等 MM 渲染器保持一致。
     */
    @Override
    public void render(RenderLevelStageEvent event, MultiBufferSource bufferSource, float partialTicks) {
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        var camPos = camera.getPosition();

        // 获取完整光照数据（打包的天空+方块光照）
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int light = LevelRenderer.getLightColor(mc.level, camera.getBlockPosition());
        List<ParticleEmitterInstance> emitters = ParticleEmitterManager.getInstance().getActiveEmitters();

        // PoseStack 偏移到摄像机相对空间（AFTER_ENTITIES 阶段需手动处理摄像机平移）
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        renderer.renderAll(emitters, pose, bufferSource, camera, partialTicks, light);

        pose.popPose();

        // 显式提交缓冲区。
        // ADDITIVE_TRANSPARENCY 在 BufferSource 中属于非 translucent 阶段的 phase，
        // 若不手动 endBatch，渲染会推迟到 LevelRenderer 后续步骤（天气/云）之后才提交，
        // 届时 GL 状态已被修改，导致深度测试行为异常，粒子永远被实体遮挡。
        ((MultiBufferSource.BufferSource)bufferSource).endBatch();
    }
}
