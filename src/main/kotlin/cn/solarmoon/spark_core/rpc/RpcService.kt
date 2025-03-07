package cn.solarmoon.spark_core.rpc

interface RpcService {
    fun callMethod(methodName: String, params: Map<String, String>): Any?
} 