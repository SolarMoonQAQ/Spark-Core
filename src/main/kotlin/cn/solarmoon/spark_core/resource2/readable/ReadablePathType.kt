package cn.solarmoon.spark_core.resource2.readable

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import java.nio.file.Path

/**
 * 在拓展文件夹中可以被读取为拓展包的文件类型，并提供读取为package的方法
 */
interface ReadablePathType {

    fun match(path: Path): Boolean

    fun readAsPackage(path: Path): SparkPackage

}