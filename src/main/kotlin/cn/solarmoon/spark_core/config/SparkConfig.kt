package cn.solarmoon.spark_core.config

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.ModContainer
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.function.Supplier

/**
 * Spark Core 的配置类
 */
object SparkConfig {

    // 通用配置
    private val COMMON_BUILDER = ModConfigSpec.Builder()
    internal val COMMON_CONFIG: CommonConfig
    val COMMON_SPEC: ModConfigSpec

    // 客户端配置
    private val CLIENT_BUILDER = ModConfigSpec.Builder()
    private val CLIENT_CONFIG: ClientConfig
    val CLIENT_SPEC: ModConfigSpec

    init {
        // 初始化通用配置
        COMMON_CONFIG = CommonConfig(COMMON_BUILDER)
        COMMON_SPEC = COMMON_BUILDER.build()

        // 初始化客户端配置
        CLIENT_CONFIG = ClientConfig(CLIENT_BUILDER)
        CLIENT_SPEC = CLIENT_BUILDER.build()
    }

    /**
     * 注册配置
     *
     * @param container 模组容器
     */
    @JvmStatic
    fun register(container: ModContainer) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC)
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC)
        SparkCore.LOGGER.info("已注册配置")
    }

    /**
     * 通用配置类
     */
    class CommonConfig(builder: ModConfigSpec.Builder) {
        // Web服务器配置
        val enableWebServer: Supplier<Boolean>
        val webServerPort: Supplier<Int>
        val webServerCorsEnabled: Supplier<Boolean>

        // 打包配置
        val defaultPackagingOutputDir: Supplier<String>
        val maxConcurrentPackagingTasks: Supplier<Int>
        val packagingTaskTimeoutMinutes: Supplier<Int>
        val defaultCompressionLevel: Supplier<Int>

        init {

            // Web服务器配置
            builder.comment("Web服务器配置")
            builder.push("webserver")

            enableWebServer = builder
                .comment("是否启用web服务器，用于提供RESTful API接口进行调试和控制")
                .translation("config.${SparkCore.MOD_ID}.enable_web_server")
                .define("enableWebServer", false)

            webServerPort = builder
                .comment("web服务器监听端口")
                .translation("config.${SparkCore.MOD_ID}.web_server_port")
                .defineInRange("webServerPort", 8081, 1024, 65535)

            webServerCorsEnabled = builder
                .comment("是否启用CORS跨域支持，用于前端调试")
                .translation("config.spark_core.web_server_cors_enabled")
                .define("webServerCorsEnabled", true)

            builder.pop()

            // 打包配置
            builder.comment("打包管理配置")
            builder.push("packaging")

            defaultPackagingOutputDir = builder
                .comment("默认打包输出目录")
                .translation("config.${SparkCore.MOD_ID}.default_packaging_output_dir")
                .define("defaultPackagingOutputDir", "sparkcore/packages")

            maxConcurrentPackagingTasks = builder
                .comment("最大并发打包任务数")
                .translation("config.${SparkCore.MOD_ID}.max_concurrent_packaging_tasks")
                .defineInRange("maxConcurrentPackagingTasks", 2, 1, 8)

            packagingTaskTimeoutMinutes = builder
                .comment("打包任务超时时间（分钟）")
                .translation("config.${SparkCore.MOD_ID}.packaging_task_timeout_minutes")
                .defineInRange("packagingTaskTimeoutMinutes", 30, 5, 120)

            defaultCompressionLevel = builder
                .comment("默认压缩级别（0-9）")
                .translation("config.${SparkCore.MOD_ID}.default_compression_level")
                .defineInRange("defaultCompressionLevel", 9, 0, 9)

            builder.pop()
        }
    }

    /**
     * 客户端配置类
     */
    class ClientConfig(builder: ModConfigSpec.Builder) {
        // 可以在这里添加客户端特定的配置

        init {
            // 暂时没有客户端特定配置
        }
    }
}
