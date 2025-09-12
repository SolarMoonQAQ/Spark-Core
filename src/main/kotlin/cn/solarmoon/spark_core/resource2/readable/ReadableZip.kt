package cn.solarmoon.spark_core.resource2.readable

import cn.solarmoon.spark_core.resource2.SparkPackLoader.META_PATH
import cn.solarmoon.spark_core.resource2.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.sequences.forEach

object ReadableZip: ReadablePathType {

    override fun match(path: Path) = path.isRegularFile() && path.toString().endsWith(".zip")

    override fun readAllEntries(path: Path): LinkedHashMap<String, ByteArray> {
        val file = path.toFile()
        val map = linkedMapOf<String, ByteArray>()
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .forEach { entry ->
                    val content = zip.getInputStream(entry).readAllBytes()
                    map[entry.name] = content
                }
            return map
        }
    }

}