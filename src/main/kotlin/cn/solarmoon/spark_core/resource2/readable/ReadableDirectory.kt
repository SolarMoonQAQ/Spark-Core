package cn.solarmoon.spark_core.resource2.readable

import cn.solarmoon.spark_core.resource2.SparkPackLoader.META_PATH
import cn.solarmoon.spark_core.resource2.graph.SparkPackMetaInfo
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

object ReadableDirectory: ReadablePathType {

    override fun match(path: Path) = path.isDirectory()

    override fun readAllEntries(path: Path): LinkedHashMap<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        Files.walk(path).use { walk ->
            walk.filter { it.isRegularFile() }
                .forEach { file ->
                    val relative = path.relativize(file).toString().replace("\\", "/")
                    map[relative] = Files.readAllBytes(file)
                }
        }
        return map
    }

}