package cn.solarmoon.spark_core.particle.client.render;

import cn.solarmoon.spark_core.particle.common.data.ParticlePreset.BillboardMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Billboard 四边形几何构建辅助类。
 * <p>
 * 采用 PoseStack 管线方案：
 * <ol>
 *   <li>ParticleRenderer 已通过 pose.translate() 将局部原点移到粒子世界坐标</li>
 *   <li>此方法通过 {@code pose.mulPose()} 叠加朝向旋转和自旋</li>
 *   <li>在局部 XY 平面原点构建四边形，PoseStack 的视图变换自动处理世界→屏幕映射</li>
 * </ol>
 * <h3>朝向原理</h3>
 * AFTER_ENTITIES 阶段的 PoseStack 已包含视图矩阵 {@code M_view}。
 * XY 平面四边形（法线 (0,0,1)）直接提交会被 {@code M_view} 旋转，导致固定朝向世界 -Z。
 * 需要叠加摄像机旋转的"逆方向"旋转来抵消：
 * <ul>
 *   <li>{@code ROTATE_XYZ}：同时抵消 yaw 和 pitch，面片始终正对摄像机</li>
 *   <li>{@code ROTATE_Y}：仅抵消 yaw + pitch + roll，面片绕世界 Y 轴旋转</li>
 *   <li>{@code DIRECTION_*}：通过视图矩阵获取世界轴方向，用 rotateTo 对齐</li>
 *   <li>{@code LOOKAT_DIRECTION}：速度经视图变换后用 rotateTo 对齐</li>
 * </ul>
 */
public final class BillboardHelper {

    // 可复用临时变量
    private static final Vector3f TEMP_DIR = new Vector3f();
    private static final Vector3f TEMP_AXIS = new Vector3f();
    private static final Matrix4f TEMP_MAT = new Matrix4f();

    private BillboardHelper() {}

    /**
     * 在 PoseStack 上应用 Billboard 朝向旋转 + 粒子自旋，然后构建四边形。
     * <p>
     * 调用前 PoseStack 应已通过 translate() 偏移到粒子世界坐标。
     *
     * @param vc          顶点消费者
     * @param pose        PoseStack（已 translate 到粒子位置）
     * @param viewPose    视图矩阵快照（用于 DIRECTION_* / LOOKAT_DIRECTION 模式计算世界轴方向）
     * @param camera      摄像机（从中获取 yaw / pitch / roll）
     * @param width,height 粒子宽高
     * @param u0,v0,u1,v1 UV 坐标
     * @param r,g,b,a     颜色
     * @param rotation    粒子自旋角（度）
     * @param mode        Billboard 朝向模式
     * @param velX,velY,velZ 粒子速度（用于 LOOKAT_DIRECTION）
     * @param light       光照值
     */
    public static void renderBillboard(VertexConsumer vc, PoseStack pose,
                                        Matrix4f viewPose, Camera camera,
                                        float width, float height,
                                        float u0, float v0, float u1, float v1,
                                        float r, float g, float b, float a,
                                        float rotation, BillboardMode mode,
                                        float velX, float velY, float velZ,
                                        int light) {
        float hw = width / 2f;
        float hh = height / 2f;

        // 1. 应用 Billboard 朝向旋转（抵消/部分抵消摄像机旋转）
        applyBillboardRotation(pose, viewPose, camera, mode, velX, velY, velZ);

        // 2. 应用粒子自旋（作用于 XY 平面）
        if (rotation != 0) {
            pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(rotation)));
        }

        // 3. 在局部原点构建 XY 平面四边形
        var matrix = pose.last();

        vertex(vc, matrix, -hw, -hh, 0, u0, v1, r, g, b, a, light);
        vertex(vc, matrix, -hw, +hh, 0, u0, v0, r, g, b, a, light);
        vertex(vc, matrix, +hw, +hh, 0, u1, v0, r, g, b, a, light);
        vertex(vc, matrix, +hw, -hh, 0, u1, v1, r, g, b, a, light);
    }

    /**
     * 根据朝向模式，在 PoseStack 上叠加摄像机旋转的"逆抵消"。
     * <p>
     * 核心公式：PoseStack 累积矩阵 = {@code M_view * M_model}。
     * 四边形法线 (0,0,1) 经 {@code M_view} 旋转后固定指向世界 -Z。
     * 通过在 M_model 中叠加上摄像机旋转的逆方向，使 {@code M_view * M_model}
     * 对方向的净效果为所需的朝向。
     */
    private static void applyBillboardRotation(PoseStack pose, Matrix4f viewPose,
                                                Camera camera, BillboardMode mode,
                                                float velX, float velY, float velZ) {
        switch (mode) {
            case ROTATE_XYZ, LOOKAT_XYZ -> {
                // 同时抵消 yaw 和 pitch，使面片始终正对摄像机
                // YN = 绕Y轴负方向 → 抵消摄像机的yaw
                // XP = 绕X轴正方向 → 抵消摄像机的pitch
                pose.mulPose(Axis.YN.rotationDegrees(camera.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            }
            case ROTATE_Y, LOOKAT_Y -> {
                // 仅抵消 yaw：无变换时面片固定朝向 world -Z，
                // 补 -yaw 后跟随摄像机水平旋转，面片仅在 Y 轴方向旋转
                pose.mulPose(Axis.YN.rotationDegrees(camera.getYRot()));
            }
            case DIRECTION_X -> {
                // 面片法线朝世界 X 轴：取视图矩阵第 0 列（世界 X 在视图空间的表示）
                viewPose.getColumn(0, TEMP_AXIS);
                TEMP_AXIS.normalize();
                pose.mulPose(new Quaternionf().rotateTo(0, 0, 1, TEMP_AXIS.x, TEMP_AXIS.y, TEMP_AXIS.z));
            }
            case DIRECTION_Y -> {
                viewPose.getColumn(1, TEMP_AXIS);
                TEMP_AXIS.normalize();
                pose.mulPose(new Quaternionf().rotateTo(0, 0, 1, TEMP_AXIS.x, TEMP_AXIS.y, TEMP_AXIS.z));
            }
            case DIRECTION_Z -> {
                viewPose.getColumn(2, TEMP_AXIS);
                TEMP_AXIS.normalize();
                pose.mulPose(new Quaternionf().rotateTo(0, 0, 1, TEMP_AXIS.x, TEMP_AXIS.y, TEMP_AXIS.z));
            }
            case LOOKAT_DIRECTION -> {
                // 将速度方向变换到视图空间，然后对齐法线
                transformDirection(TEMP_DIR, viewPose, velX, velY, velZ);
                float lenSq = TEMP_DIR.x * TEMP_DIR.x + TEMP_DIR.y * TEMP_DIR.y + TEMP_DIR.z * TEMP_DIR.z;
                if (lenSq > 0.0001f) {
                    TEMP_DIR.normalize();
                    pose.mulPose(new Quaternionf().rotateTo(0, 0, 1, TEMP_DIR.x, TEMP_DIR.y, TEMP_DIR.z));
                }
            }
            default -> {
                // EMITTER_TRANSFORM_XY / XZ / YZ 暂未实现
            }
        }
    }

    /**
     * 用矩阵的 3×3 部分变换一个方向向量。
     */
    private static void transformDirection(Vector3f dest, Matrix4f mat, float x, float y, float z) {
        dest.set(
                mat.m00() * x + mat.m10() * y + mat.m20() * z,
                mat.m01() * x + mat.m11() * y + mat.m21() * z,
                mat.m02() * x + mat.m12() * y + mat.m22() * z
        );
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose matrix,
                                float x, float y, float z,
                                float u, float v,
                                float r, float g, float b, float a,
                                int light) {
        vc.addVertex(matrix, x, y, z)
                .setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);
    }
}
