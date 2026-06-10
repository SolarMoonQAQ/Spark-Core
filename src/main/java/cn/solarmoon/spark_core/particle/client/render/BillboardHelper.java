package cn.solarmoon.spark_core.particle.client.render;

import cn.solarmoon.spark_core.particle.common.data.ParticlePreset.BillboardMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Billboard 四边形几何构建辅助类。
 * <p>
 * 支持 11 种基岩版朝向模式：ROTATE_XYZ, ROTATE_Y, LOOKAT_XYZ, LOOKAT_Y,
 * LOOKAT_DIRECTION, DIRECTION_X, DIRECTION_Y, DIRECTION_Z,
 * EMITTER_TRANSFORM_XY, EMITTER_TRANSFORM_XZ, EMITTER_TRANSFORM_YZ。
 * <p>
 * 核心方法 {@link #renderBillboard} 将粒子在视图空间中构建为面向摄像机的四边形。
 */
public final class BillboardHelper {

    // 可复用的临时变量，避免每粒子渲染时分配新对象
    private static final Matrix4f TEMP_POSE = new Matrix4f();
    private static final Vector4f TEMP_VIEW_POS = new Vector4f();
    private static final Vector3f TEMP_AXIS_X = new Vector3f();
    private static final Vector3f TEMP_AXIS_Y = new Vector3f();
    private static final Vector3f TEMP_DIR = new Vector3f();
    private static final Matrix4f IDENTITY_POSE = new Matrix4f();
    private static final Matrix3f IDENTITY_NORMAL = new Matrix3f();

    private BillboardHelper() {}

    /**
     * 在 PoseStack 的局部坐标系中渲染一个 billboard 四边形。
     * <p>
     * 方法将粒子坐标通过 effectivePose 变换到视图空间，然后根据朝向模式
     * 计算 quad 的 X/Y 轴，最后在视图空间中构建四个顶点。
     *
     * @param particleX, particleY, particleZ  粒子在模型/世界空间中的位置
     * @param width, height  粒子宽度和高度（局部坐标系单位）
     * @param u0, v0, u1, v1  UV 坐标
     * @param r, g, b, a  颜色（0~1 范围）
     * @param rotation  粒子自旋角度（度）
     * @param mode  Billboard 朝向模式
     * @param cameraPitch  摄像机 pitch（弧度），由 {@link Camera#getXRot()} 转换
     * @param cameraRoll   摄像机 roll（弧度），由 {@link CameraStateCache} 获取
     */
    public static void renderBillboard(VertexConsumer consumer, PoseStack poseStack, int light,
                                       float particleX, float particleY, float particleZ,
                                       float width, float height,
                                       float u0, float v0, float u1, float v1,
                                       float r, float g, float b, float a,
                                       float rotation, BillboardMode mode,
                                       float cameraPitch, float cameraRoll) {

        Matrix4f pose = poseStack.last().pose();

        // 将粒子坐标变换到视图空间
        TEMP_VIEW_POS.set(particleX, particleY, particleZ, 1);
        pose.transform(TEMP_VIEW_POS);

        float hw = width / 2;
        float hh = height / 2;

        // 计算 billboard 的两个轴向量（视图空间中）
        Vector3f axisX = TEMP_AXIS_X.set(1, 0, 0);
        Vector3f axisY = TEMP_AXIS_Y.set(0, 1, 0);
        applyBillboardAxes(axisX, axisY, mode, pose, cameraPitch, cameraRoll);

        // 应用粒子自旋旋转（在视图空间 XY 平面内旋转轴向量）
        if (rotation != 0) {
            float rad = (float) Math.toRadians(rotation);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float ax = axisX.x, ay = axisX.y, az = axisX.z;
            float bx = axisY.x, by = axisY.y, bz = axisY.z;
            axisX.set(ax * cos + bx * sin, ay * cos + by * sin, az * cos + bz * sin);
            axisY.set(-ax * sin + bx * cos, -ay * sin + by * cos, -az * sin + bz * cos);
        }

        // 在视图空间中构建 4 个顶点（使用 identity 矩阵，坐标已视图空间）
        float cx = TEMP_VIEW_POS.x, cy = TEMP_VIEW_POS.y, cz = TEMP_VIEW_POS.z;
        IDENTITY_POSE.identity();
        IDENTITY_NORMAL.identity();

        vertex(consumer, IDENTITY_POSE, IDENTITY_NORMAL,
                cx - axisX.x * hw - axisY.x * hh,
                cy - axisX.y * hw - axisY.y * hh,
                cz - axisX.z * hw - axisY.z * hh,
                u0, v1, r, g, b, a, light);
        vertex(consumer, IDENTITY_POSE, IDENTITY_NORMAL,
                cx - axisX.x * hw + axisY.x * hh,
                cy - axisX.y * hw + axisY.y * hh,
                cz - axisX.z * hw + axisY.z * hh,
                u0, v0, r, g, b, a, light);
        vertex(consumer, IDENTITY_POSE, IDENTITY_NORMAL,
                cx + axisX.x * hw + axisY.x * hh,
                cy + axisX.y * hw + axisY.y * hh,
                cz + axisX.z * hw + axisY.z * hh,
                u1, v0, r, g, b, a, light);
        vertex(consumer, IDENTITY_POSE, IDENTITY_NORMAL,
                cx + axisX.x * hw - axisY.x * hh,
                cy + axisX.y * hw - axisY.y * hh,
                cz + axisX.z * hw - axisY.z * hh,
                u1, v1, r, g, b, a, light);
    }

    /**
     * 极简版本：直接在世界空间中构建 Billboard，无需 PoseStack。
     * 适用于世界空间粒子的快速渲染。
     */
    public static void renderBillboardDirect(VertexConsumer vc,
                                             float x, float y, float z,
                                             float width, float height,
                                             float u0, float v0, float u1, float v1,
                                             float r, float g, float b, float a,
                                             BillboardMode mode,
                                             Camera camera, int light) {

        Vector3f cameraPos = new Vector3f((float) camera.getPosition().x, (float) camera.getPosition().y, (float) camera.getPosition().z);
        Vector3f toCamera = new Vector3f(cameraPos).sub(x, y, z).normalize();

        Vector3f axisX = new Vector3f();
        Vector3f axisY = new Vector3f();

        switch (mode) {
            case ROTATE_XYZ -> {
                // 标准 Billboard: 法线朝向摄像机
                Vector3f up = new Vector3f(0, 1, 0);
                axisX.set(toCamera).cross(up).normalize();
                if (axisX.length() < 0.001f) axisX.set(1, 0, 0);
                axisY.set(axisX).cross(toCamera).normalize();
                axisY.negate();
            }
            case ROTATE_Y -> {
                // 仅绕 Y 轴旋转：法线在水平面上朝向摄像机
                Vector3f flatDir = new Vector3f(toCamera.x, 0, toCamera.z).normalize();
                axisX.set(flatDir.z, 0, -flatDir.x);
                axisY.set(0, 1, 0);
            }
            default -> {
                axisX.set(1, 0, 0);
                axisY.set(0, 1, 0);
            }
        }

        float hw = width / 2;
        float hh = height / 2;

        // 计算四个世界空间顶点
        Vector3f p0 = new Vector3f(x, y, z).add(new Vector3f(axisX).mul(-hw)).add(new Vector3f(axisY).mul(-hh));
        Vector3f p1 = new Vector3f(x, y, z).add(new Vector3f(axisX).mul(+hw)).add(new Vector3f(axisY).mul(-hh));
        Vector3f p2 = new Vector3f(x, y, z).add(new Vector3f(axisX).mul(+hw)).add(new Vector3f(axisY).mul(+hh));
        Vector3f p3 = new Vector3f(x, y, z).add(new Vector3f(axisX).mul(-hw)).add(new Vector3f(axisY).mul(+hh));

        Vector3f normal = new Vector3f(toCamera).normalize();

        addVertex(vc, p0.x, p0.y, p0.z, u0, v1, r, g, b, a, normal.x, normal.y, normal.z, light);
        addVertex(vc, p1.x, p1.y, p1.z, u1, v1, r, g, b, a, normal.x, normal.y, normal.z, light);
        addVertex(vc, p2.x, p2.y, p2.z, u1, v0, r, g, b, a, normal.x, normal.y, normal.z, light);
        addVertex(vc, p3.x, p3.y, p3.z, u0, v0, r, g, b, a, normal.x, normal.y, normal.z, light);
    }

    /**
     * 根据朝向模式计算 billboard 的 X/Y 轴方向（视图空间中的单位向量）。
     * axisX 对应 quad 的宽度方向，axisY 对应高度方向。
     *
     * @param axisX, axisY  输出参数，写入视图空间中的方向向量
     * @param mode  朝向模式
     * @param viewPose  视图矩阵（poseStack 的 pose），用于将世界坐标轴变换到视图空间
     * @param cameraPitch  摄像机 pitch（弧度）
     * @param cameraRoll   摄像机 roll（弧度）
     */
    private static void applyBillboardAxes(Vector3f axisX, Vector3f axisY,
                                           BillboardMode mode,
                                           Matrix4f viewPose,
                                           float cameraPitch, float cameraRoll) {
        switch (mode) {
            case ROTATE_XYZ, LOOKAT_XYZ -> {
                axisX.set(1, 0, 0);
                axisY.set(0, 1, 0);
            }
            case ROTATE_Y, LOOKAT_Y -> {
                // 世界 Y 轴在视图空间中的投影（考虑 pitch 和 roll）
                float cosPitch = (float) Math.cos(cameraPitch);
                float sinPitch = (float) Math.sin(cameraPitch);
                float ax = 1, ay = 0;
                float bx = 0, by = cosPitch, bz = -sinPitch;

                if (cameraRoll != 0) {
                    float cosRoll = (float) Math.cos(cameraRoll);
                    float sinRoll = (float) Math.sin(cameraRoll);
                    axisX.set(ax * cosRoll + bx * sinRoll, ay * cosRoll + by * sinRoll, bz * sinRoll);
                    axisY.set(-ax * sinRoll + bx * cosRoll, -ay * sinRoll + by * cosRoll, bz * cosRoll);
                } else {
                    axisX.set(ax, ay, 0);
                    axisY.set(bx, by, bz);
                }
            }
            case LOOKAT_DIRECTION -> {
                // 由粒子速度方向决定的朝向，需外部传入速度
                // 保留轴不变，由调用方处理
                axisX.set(1, 0, 0);
                axisY.set(0, 1, 0);
            }
            case DIRECTION_X -> {
                // 面片法线朝世界 X 轴，面片在 YZ 平面上
                transformDirection(axisX, viewPose, 0, 0, 1);
                axisX.normalize();
                transformDirection(axisY, viewPose, 0, 1, 0);
                axisY.normalize();
            }
            case DIRECTION_Y -> {
                // 面片法线朝世界 Y 轴，面片在 XZ 平面上
                transformDirection(axisX, viewPose, 1, 0, 0);
                axisX.normalize();
                transformDirection(axisY, viewPose, 0, 0, 1);
                axisY.normalize();
            }
            case DIRECTION_Z -> {
                // 面片法线朝世界 Z 轴，面片在 XY 平面上
                transformDirection(axisX, viewPose, 1, 0, 0);
                axisX.normalize();
                transformDirection(axisY, viewPose, 0, 1, 0);
                axisY.normalize();
            }
            default -> {
                // EMITTER_TRANSFORM_* 模式：保留默认轴，由外部变换矩阵处理
            }
        }
    }

    /**
     * 用矩阵的 3x3 部分（含旋转）变换一个方向向量，结果写入 dest。
     */
    private static void transformDirection(Vector3f dest, Matrix4f pose, float x, float y, float z) {
        dest.set(
                pose.m00() * x + pose.m10() * y + pose.m20() * z,
                pose.m01() * x + pose.m11() * y + pose.m21() * z,
                pose.m02() * x + pose.m12() * y + pose.m22() * z
        );
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z,
                               float u, float v, float r, float g, float b, float a, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 1, 0);
    }

    private static void addVertex(VertexConsumer vc,
                                  float x, float y, float z,
                                  float u, float v, float r, float g, float b, float a,
                                  float nx, float ny, float nz, int light) {
        vc.addVertex(x, y, z)
                .setColor((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
