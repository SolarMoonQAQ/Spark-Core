package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType

class FontModule : SparkPackModule {

    override val id: String = "font"
    override val mode: ReadMode = ReadMode.CLIENT_LOCAL_ONLY

    private var jsonCount = 0
    private var ttfCount = 0

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && !fromServer) {
            jsonCount = 0
            ttfCount = 0
            SparkCore.LOGGER.info("开始加载外部包字体资源…")
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (fromServer) return
        if (!isClientSide) return

        when {
            fileName.endsWith(".json") -> {
                val path = buildString {
                    append("font/")
                    if (pathSegments.isNotEmpty()) {
                        append(pathSegments.joinToString("/"))
                        append("/")
                    }
                    append(fileName)
                }
                SparkPackLoaderApplier.CLIENT_PACK.put(
                    PackType.CLIENT_RESOURCES,
                    ResourceLocation.fromNamespaceAndPath(namespace, path),
                    content
                )
                jsonCount++
            }

            fileName.endsWith(".ttf") -> {
                val path = buildString {
                    append("font/")
                    if (pathSegments.isNotEmpty()) {
                        append(pathSegments.joinToString("/"))
                        append("/")
                    }
                    append(fileName)
                }
                SparkPackLoaderApplier.CLIENT_PACK.put(
                    PackType.CLIENT_RESOURCES,
                    ResourceLocation.fromNamespaceAndPath(namespace, path),
                    content
                )
                ttfCount++
            }
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && !fromServer) {
            SparkCore.LOGGER.info(
                "从外部包注册了 {} 个字体定义（json），{} 个字体文件（ttf）",
                jsonCount,
                ttfCount
            )
        }
    }
}
