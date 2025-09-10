package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("Logger")
object JSLoggerGlobal {

    val logger = SparkJS.LOGGER

    fun info(message: String) {
        logger.info(message)
    }

}