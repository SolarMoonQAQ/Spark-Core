package cn.solarmoon.spark_core.js.modules

import cn.solarmoon.spark_core.js.JavaScript

class DefaultJSModule: JSModule {

    companion object {
        const val ID = "default"
    }

    override val id: String = ID

    override fun onLoaded(script: JavaScript) {}

}