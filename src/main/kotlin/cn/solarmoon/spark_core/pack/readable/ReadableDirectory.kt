package cn.solarmoon.spark_core.pack.readable

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