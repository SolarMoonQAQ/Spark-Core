package cn.solarmoon.spark_core.web.dto

import java.time.Instant

/**
 * 标准化的API响应格式
 * @param T 响应数据的类型
 * @param success 操作是否成功
 * @param data 响应数据，可为null
 * @param message 响应消息，用于描述操作结果或错误信息
 * @param timestamp 响应时间戳
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String = "",
    val timestamp: String = Instant.now().toString()
) {
    companion object {
        /**
         * 创建成功响应
         */
        @JvmStatic
        fun <T> success(data: T? = null, message: String = "操作成功"): ApiResponse<T> {
            return ApiResponse(success = true, data = data, message = message)
        }

        /**
         * 创建错误响应
         */
        @JvmStatic
        fun <T> error(message: String, data: T? = null): ApiResponse<T> {
            return ApiResponse(success = false, data = data, message = message)
        }
    }
}
