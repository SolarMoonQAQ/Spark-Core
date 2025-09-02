package cn.solarmoon.spark_core.resource2.readable

import cn.solarmoon.spark_core.resource2.SparkPackLoader.META_NAME
import cn.solarmoon.spark_core.resource2.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

object ReadableDirectory: ReadablePathType {

    override fun match(path: Path) = path.isDirectory()

    override fun readAsPackage(path: Path): SparkPackage {
        val metaPath = path.resolve(META_NAME)
        if (!Files.exists(metaPath)) { throw IllegalArgumentException("缺少 $META_NAME 元数据文件") }
        val metaJson = Files.readString(metaPath, StandardCharsets.UTF_8)
        val meta = SparkPackMetaInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(metaJson)).getOrThrow { throw IllegalArgumentException("元数据解析失败: $it") }
        val entriesMap = mutableMapOf<String, ByteArray>()
        Files.walk(path).use { walk ->
            walk.filter { Files.isRegularFile(it) && it != metaPath }
                .forEach { file ->
                    val relative = path.relativize(file).toString().replace("\\", "/")
                    entriesMap[relative] = Files.readAllBytes(file)
                }
        }
        return SparkPackage(meta, entriesMap)
    }

}