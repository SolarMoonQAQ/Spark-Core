package cn.solarmoon.spark_core.pack.readable

import cn.solarmoon.spark_core.pack.SparkPackLoader
import cn.solarmoon.spark_core.pack.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * 在拓展文件夹中可以被读取为拓展包的文件类型，并提供读取为package的方法
 */
interface ReadablePathType {

    fun match(path: Path): Boolean

    fun readAllEntries(path: Path): LinkedHashMap<String, ByteArray>

    fun readAsPackage(path: Path, isClientSide: Boolean): SparkPackage {
        val source = readAllEntries(path)

        // 找 META
        val metaEntry = source.entries.firstOrNull { it.key == SparkPackLoader.META_PATH }
            ?: throw IllegalArgumentException("缺少 ${SparkPackLoader.META_PATH} 元数据文件")

        val metaJson = metaEntry.value.toString(StandardCharsets.UTF_8)
        val meta = SparkPackMetaInfo.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString(metaJson))
            .getOrThrow { throw IllegalArgumentException("元数据解析失败: $it") }

        // 过滤非 namespace/模块/文件 结构
        val filtered = source
            .filter { it.key != SparkPackLoader.META_PATH }
            .filter { path ->
                val parts = path.key.split("/")
                if (parts.size < 3) return@filter false

                val moduleId = parts[1]
                val mode = SparkPackLoader.getModule(moduleId)?.mode
                mode?.shouldRead(isClientSide) == true
            }

        // namespace → module → files
        val grouped = filtered.entries
            .groupBy(
                keySelector = { it.key.substringBefore("/") }, // namespace
                valueTransform = { entry ->
                    val remainder = entry.key.substringAfter("/")
                    val moduleId = remainder.substringBefore("/")
                    val innerPath = remainder.substringAfter("/")
                    moduleId to (innerPath to entry.value)
                }
            )
            .mapValues { (_, values) ->
                values
                    .groupBy({ it.first }) { it.second }
                    .mapValues { (_, files) ->
                        files.toMap().toMutableMap()
                    }
                    .toMutableMap()
            }
            .toMutableMap()

        return SparkPackage(meta, grouped)
    }

}
