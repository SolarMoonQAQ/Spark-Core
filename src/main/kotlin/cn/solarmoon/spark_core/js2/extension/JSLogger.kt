package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSComponent

object JSLogger: JSComponent() {

    fun info(msg: String) = SparkCore.LOGGER.info(msg)

}