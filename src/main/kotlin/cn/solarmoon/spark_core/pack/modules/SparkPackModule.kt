package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.google.gson.Gson
import com.google.gson.GsonBuilder

interface SparkPackModule {

    val id: String

    val mode: ReadMode get() = ReadMode.SERVER_TO_CLIENT

    companion object {
        val gson: Gson = GsonBuilder().create()
    }

    fun onInitialize(isClientSide: Boolean) {}

    /**
     * 所有该模块内容开始读取之前调用此方法
     * @param isClientSide 是否为客户端
     * @param fromServer   是否来自服务器
     * @param isClientSide 是否为客户端
     * @param fromServer   数据/调用是否来自服务器
     */
    fun onStart(isClientSide: Boolean, fromServer: Boolean) {}

    /**
     * @param namespace    模块命名空间
     * @param pathSegments 模块目录下的层层递进目录（不含模块名和文件名）（比如 minecraft/animations/player/test.json，此参数则为list["player"]）
     * @param fileName     文件名
     * @param content      文件内容（字节码）
     * @param pack         当前压缩包
     * @param isClientSide 是否为客户端
     * @param fromServer   数据/调用是否来自服务器
     */
    fun read(
        namespace: String, pathSegments: List<String>, fileName: String, content: ByteArray, pack: SparkPackage, isClientSide: Boolean, fromServer: Boolean)

    /**
     * 所有该模块内容读取完毕后调用此方法
     * @param isClientSide 是否为客户端
     * @param fromServer   数据/调用是否来自服务器
     */
    fun onFinish(isClientSide: Boolean, fromServer: Boolean) {}

}