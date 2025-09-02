package cn.solarmoon.spark_core.resource2.readable

import cn.solarmoon.spark_core.resource2.SparkPackLoader.META_NAME
import cn.solarmoon.spark_core.resource2.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.sequences.forEach

object ReadableZip: ReadablePathType {

    override fun match(path: Path) = path.isRegularFile() && path.toString().endsWith(".zip")

    override fun readAsPackage(path: Path): SparkPackage {
        val file = path.toFile()
        ZipFile(file).use { zip ->
            // 读取 meta
            val metaFile = zip.getEntry(META_NAME) ?: throw IllegalArgumentException("缺少 $META_NAME 元数据文件")
            val metaJson = zip.getInputStream(metaFile).bufferedReader(StandardCharsets.UTF_8).readText()
            val meta = SparkPackMetaInfo.Companion.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(metaJson)).getOrThrow { throw IllegalArgumentException("元数据解析失败: $it") }
            // 读取所有文件到 Map
            val entriesMap = mutableMapOf<String, ByteArray>()
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .forEach { entry ->
                    val content = zip.getInputStream(entry).readAllBytes()
                    entriesMap[entry.name] = content
                }
            return SparkPackage(meta, entriesMap)
        }
    }

}