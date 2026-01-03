package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.pack.graph.SparkPackage

interface SparkPackModule {

    val id: String

    val mode: ReadMode get() = ReadMode.SERVER_TO_CLIENT

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
     * @param pathSegments 模块目录下的层层递进目录（不含模块名和文件名）（比如 animations/minecraft/player/test.json，此参数则为list["minecraft", "player"]）
     * @param fileName     文件名
     * @param content      文件内容（字节码）
     * @param pack         当前压缩包
     * @param isClientSide 是否为客户端
     * @param fromServer   数据/调用是否来自服务器
     */
    fun read(pathSegments: List<String>, fileName: String, content: ByteArray, pack: SparkPackage, isClientSide: Boolean, fromServer: Boolean)

    /**
     * 所有该模块内容读取完毕后调用此方法
     * @param isClientSide 是否为客户端
     * @param fromServer   数据/调用是否来自服务器
     */
    fun onFinish(isClientSide: Boolean, fromServer: Boolean) {}

}