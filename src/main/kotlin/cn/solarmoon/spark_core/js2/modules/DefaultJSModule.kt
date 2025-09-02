package cn.solarmoon.spark_core.js2.modules

import cn.solarmoon.spark_core.js2.JavaScript

class DefaultJSModule: JSModule {

    companion object {
        const val ID = "default"
    }

    override val id: String = ID

    override fun onLoaded(script: JavaScript) {}

}