package cn.solarmoon.spark_core.particle.client.render;

import cn.solarmoon.spark_core.particle.client.ParticleArray;
import cn.solarmoon.spark_core.particle.client.ParticleDoubleBuffer;
import cn.solarmoon.spark_core.particle.client.ParticleEmitterInstance;
import cn.solarmoon.spark_core.particle.common.data.ParticlePreset.BillboardMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 粒子 Billboard 渲染器入口。
 * <p>
 * 遍历所有发射器，对每个存活粒子进行 partialTick lerp 插值后提交 Billboard 顶点。
 * 通过 {@link CameraStateCache} 获取摄像机 roll 角度。
 * 建议挂在 RenderLevelStageEvent.Stage.AFTER_ENTITIES 阶段。
 */
public class ParticleRenderer {

    private final CameraStateCache cameraState = new CameraStateCache();

    /**
     * 渲染单个发射器中的所有粒子。
     *
     * @param emitter      粒子发射器实例
     * @param pose         当前 PoseStack
     * @param buffer       MultiBufferSource
     * @param camera       摄像机
     * @param partialTick  部分 tick 值
     * @param light        光照包
     */
    public void renderEmitter(ParticleEmitterInstance emitter, PoseStack pose,
                              MultiBufferSource buffer, Camera camera,
                              float partialTick, int light) {

        ParticleDoubleBuffer db = emitter.getDoubleBuffer();
        ParticleArray buf = db.getRender(); // volatile 读

        int count = buf.getCount();
        if (count == 0) return;

        ResourceLocation texture = emitter.getDefinition().getDescription().getTexture();
        String material = emitter.getDefinition().getDescription().getMaterial();
        BillboardMode mode = emitter.getDefinition().getParticlePreset().getFaceCameraMode();

        RenderType renderType = ParticleRenderType.fromMaterial(material, texture);
        VertexConsumer vc = buffer.getBuffer(renderType);

        // Local space 模式：粒子坐标是相对于 emitter 的，PoseStack 偏移到 emitter 位置
        if (emitter.getDefinition().getEmitterPreset().hasLocalPosition()) {
            Vec3 pos = emitter.getPosition();
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
        }

        // 摄像机角度
        float cameraPitch = (float) Math.toRadians(camera.getXRot());
        float cameraRoll = CameraStateCache.getCameraRollRadians();

        for (int i = 0; i < count; i++) {
            if (!buf.isAlive(i)) continue;

            // lerp 插值位置（纯读，无副作用）
            float rx = buf.getRenderX(i, partialTick);
            float ry = buf.getRenderY(i, partialTick);
            float rz = buf.getRenderZ(i, partialTick);

            // 渲染 Billboard 四边形
            BillboardHelper.renderBillboard(vc, pose, light,
                    rx, ry, rz,
                    buf.getWidth(i), buf.getHeight(i),
                    buf.getU0(i), buf.getV0(i), buf.getU1(i), buf.getV1(i),
                    buf.getR(i), buf.getG(i), buf.getB(i), buf.getA(i),
                    buf.getRot(i), mode,
                    cameraPitch, cameraRoll);
        }

        if (emitter.getDefinition().getEmitterPreset().hasLocalPosition()) {
            pose.popPose();
        }
    }

    /**
     * 批量渲染所有发射器的粒子。
     *
     * @param emitters    发射器列表
     * @param pose        当前 PoseStack
     * @param buffer      MultiBufferSource
     * @param camera      摄像机
     * @param partialTick 部分 tick 值
     * @param light       光照包
     */
    public void renderAll(java.util.List<ParticleEmitterInstance> emitters, PoseStack pose,
                          MultiBufferSource buffer, Camera camera,
                          float partialTick, int light) {
        for (ParticleEmitterInstance emitter : emitters) {
            renderEmitter(emitter, pose, buffer, camera, partialTick, light);
        }
    }
}
