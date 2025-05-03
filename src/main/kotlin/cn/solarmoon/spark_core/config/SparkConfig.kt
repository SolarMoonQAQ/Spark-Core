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
    fun register(container: ModContainer) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC)
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC)
        SparkCore.LOGGER.info("已注册配置")
    }

    /**
     * 通用配置类
     */
    class CommonConfig(builder: ModConfigSpec.Builder) {
        // JavaScript 引擎配置
        val enableGraalVM: Supplier<Boolean>

        init {
            builder.comment("JavaScript 引擎配置")
            builder.push("javascript")

            enableGraalVM = builder
                .comment("是否启用 GraalVM 功能，禁用后将无法使用 @HostAccess.Export 注解的功能")
                .translation("config.spark_core.enable_graalvm")
                .define("enableGraalVM", true)

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
