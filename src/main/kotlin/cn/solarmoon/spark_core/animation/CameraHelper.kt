package cn.solarmoon.spark_core.animation

import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.tan

/**
 * ### LOD 共享数据层
 *
 * 非 `@OnlyIn` 的共享 object，服务端和客户端均存在。
 * - **volatile 字段**：客户端 `CameraHelperClient` 写入，其他线程（物理线程、渲染线程）读取
 * - **`level` 参数分流**：所有查询方法第一个参数为 `Level`，服务端直接返回安全默认值（LOD=0）
 * - **FOV 修正**：以 70° 为基准 FOV，瞄准镜放大时 `effectiveDist` 等比缩小，LOD 自动降级
 */
object CameraHelper {

    /** 相机世界坐标，由 CameraHelperClient 每帧更新 */
    @Volatile
    var cameraPos: Vec3 = Vec3.ZERO

    /** 当前垂直 FOV（度），含其他模组修改后的最终值 */
    @Volatile
    var fov: Double = 70.0

    /** 窗口高度（像素），用于像素尺寸面剔除计算 */
    @Volatile
    var screenHeight: Int = 400

    /** 渲染距离（方块数） */
    @Volatile
    var renderDistance: Double = 32.0

    /** 基准 FOV（度），用于 LOD 距离修正计算 */
    private const val BASE_FOV = 70.0

    /**
     * 获取 LOD 等级。
     *
     * @param level 所处世界，服务端直接返回 0
     * @param worldPos 物体世界坐标
     * @param fullDetailDistance 全细节距离（此距离以内 LOD=0）
     * @return LOD 等级 0-3
     */
    @JvmStatic
    fun getLodLevel(level: Level, worldPos: Vec3, fullDetailDistance: Double): Int {
        if (!level.isClientSide) return 0

        val cameraDist = getCameraDistance(level, worldPos)
        // FOV 修正：以 70° 为基准，瞄准镜放大时 effectiveDist 等比缩小
        val currentHalfTan = tan(Math.toRadians(fov / 2.0))
        val baseHalfTan = tan(Math.toRadians(BASE_FOV / 2.0))
        val effectiveDist = cameraDist * currentHalfTan / baseHalfTan
        val ratio = effectiveDist / fullDetailDistance

        return when {
            ratio < 1.0 -> 0   // 全细节距离内
            ratio < 2.0 -> 1   // 屏幕占比减半
            ratio < 4.0 -> 2   // 屏幕占比 1/4
            else -> 3          // 更远
        }
    }

    /**
     * 获取相机到物体的距离。
     *
     * @param level 所处世界，服务端直接返回 0
     * @param worldPos 物体世界坐标
     * @return 距离（米），服务端返回 0
     */
    @JvmStatic
    fun getCameraDistance(level: Level, worldPos: Vec3): Double {
        if (!level.isClientSide) return 0.0
        return cameraPos.distanceTo(worldPos)
    }
}
