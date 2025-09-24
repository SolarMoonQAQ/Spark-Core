package cn.solarmoon.spark_core.pack.modules

enum class ReadMode {

    /**
     * 从服务端传输内容到客户端读取（以服务端为准）
     */
    SERVER_TO_CLIENT {
        override fun shouldRead(isClientSide: Boolean): Boolean {
            return !isClientSide
        }

        override fun shouldSend(): Boolean {
            return true
        }
    },

    /**
     * 双端都只读取本地文件
     */
    LOCAL_ONLY {
        override fun shouldRead(isClientSide: Boolean): Boolean {
            return true
        }

        override fun shouldSend(): Boolean {
            return false
        }
    },

    /**
     * 服务端不读取，只有客户端读取本地文件
     */
    CLIENT_LOCAL_ONLY {
        override fun shouldRead(isClientSide: Boolean): Boolean {
            return isClientSide
        }

        override fun shouldSend(): Boolean {
            return false
        }
    }

    ;

    abstract fun shouldRead(isClientSide: Boolean): Boolean

    abstract fun shouldSend(): Boolean

}