package cn.solarmoon.spark_core.particle.client.render;

import cn.solarmoon.spark_core.particle.common.data.ParticlePreset.BillboardMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/**
 * Billboard 四边形几何构建辅助类。
 * <p>
 * 使用标准 MC 渲染管线：PoseStack push/pop + translate 到粒子位置，
 * 在局部坐标系中构建四边形，PoseStack 自动处理视图/投影变换。
 * <p>
 * 支持 7 种已实现朝向模式 + 4 种占位（EMITTER_TRANSFORM_* / LOOKAT_DIRECTION）。
 */
public final class BillboardHelper {

    private BillboardHelper() {}

    /**
     * 在 PoseStack 的局部坐标系中渲染一个 billboard 四边形。
     * <p>
     * 调用方已通过 pose.pushPose() + pose.translate(particleX, particleY, particleZ)
     * 将原点偏移到粒子位置，此方法在原点周围构建四边形。
     */
    public static void renderBillboard(VertexConsumer vc, PoseStack pose, int light,
                                       float width, float height,
                                       float u0, float v0, float u1, float v1,
                                       float r, float g, float b, float a,
                                       float rotation, BillboardMode mode,
                                       Camera camera) {
        float hw = width / 2f;
        float hh = height / 2f;

        // 应用粒子自旋
        pose.pushPose();
        if (rotation != 0) {
            pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(rotation)));
        }

        // 根据朝向模式旋转 billboard
        applyBillboardRotation(pose, mode, camera);

        // 在局部原点周围构建 XY 平面上的四边形（矩阵须在 push + 旋转后获取）
        var matrix = pose.last();

        vertex(vc, matrix, -hw, -hh, u0, v1, r, g, b, a, light);
        vertex(vc, matrix, -hw, +hh, u0, v0, r, g, b, a, light);
        vertex(vc, matrix, +hw, +hh, u1, v0, r, g, b, a, light);
        vertex(vc, matrix, +hw, -hh, u1, v1, r, g, b, a, light);

        pose.popPose();
    }

    /**
     * 在 PoseStack 上叠加 Billboard 朝向旋转。
     * <p>
     * PoseStack 的当前状态已包含摄像机视角变换。
     * ROTATE_XYZ 模式下无需额外旋转（XY 平面天然面向摄像机）。
     * ROTATE_Y 需抵消摄像机的 pitch 分量。
     */
    private static void applyBillboardRotation(PoseStack pose, BillboardMode mode, Camera camera) {
        switch (mode) {
            case ROTATE_XYZ, LOOKAT_XYZ -> {
                // XY 平面由 PoseStack 的摄像机变换自动面向摄像机，无需额外旋转
            }
            case ROTATE_Y, LOOKAT_Y -> {
                // 抵消摄像机的 pitch 和 roll，使 Billboard 仅绕世界 Y 轴旋转
                pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(camera.getXRot())));
            }
            case DIRECTION_X -> pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(90)));
            case DIRECTION_Y -> pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(90)));
            case DIRECTION_Z -> {} // 默认 XY 平面
            default -> {
                // LOOKAT_DIRECTION, EMITTER_TRANSFORM_* 暂未实现
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose matrix,
                               float x, float y, float u, float v,
                               float r, float g, float b, float a, int light) {
        vc.addVertex(matrix, x, y, 0f)
                .setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);
    }
}
