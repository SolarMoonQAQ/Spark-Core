package cn.solarmoon.spark_core.particle.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 摄像机状态缓存。
 * 每帧通过 ViewportEvent.ComputeCameraAngles 事件更新摄像机 roll 角度，
 * 供 Billboard 矩阵计算使用（特别是 ROTATE_Y / LOOKAT_Y 模式）。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT)
public final class CameraStateCache {

    private static float cameraRoll;

    public CameraStateCache() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        cameraRoll = event.getRoll();
    }

    /**
     * 获取当前帧的摄像机 roll（度）。
     */
    public static float getCameraRollDegrees() {
        return cameraRoll;
    }

    /**
     * 获取当前帧的摄像机 roll（弧度）。
     */
    public static float getCameraRollRadians() {
        return (float) Math.toRadians(cameraRoll);
    }
}
