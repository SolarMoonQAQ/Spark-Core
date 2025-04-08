package cn.solarmoon.spark_core.js

interface JSApi {

    val engine: SparkJS

    val id: String

    val valueCache: MutableMap<String, String>

    fun onLoad()

    fun onRegister(engine: SparkJS)

    fun onReload()

}