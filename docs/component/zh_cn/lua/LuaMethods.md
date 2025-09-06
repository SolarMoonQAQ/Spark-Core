## 拓展脚本方法

在Lua脚本中，虽然您可以使用默认暴露出的Java内部方法，但大多数时候这些方法的传参并不和Lua的默认传参匹配，
这会导致您总是无法通过Lua使用Java类的方法，因此本页简单推荐几种方便的让Java方法和Lua方法互通的方式。

### 对于静态方法
比如，我想通过Lua直接调用代码中的逻辑，您可以像这样直接创建一个类：

````
object LuaGlobalMethods {
    fun log(msg: String) {
        println(msg)
    }
}
````

然后，通过`SparkLuaRegisterEvent`，在脚本注册时，拿到`LuaState`，然后使用

`state.setGlobal("MyMethod", LuaGlobalMethods)`

即可让lua识别到您的自定义方法，然后在lua中直接使用：

`MyMethod:log("Hello World!")`

### 对于Class
比如，我想通过Lua能够调用Entity类的一个自定义方法（注意，此方法名必须从未在代码中定义过）如`entity.customMethod`，您可以直接创建一个这样的接口：
````
interface LuaMyEntity {

    fun customMethod() {}

}
````
然后，在Mixin中直接对`Entity`类接入此接口，即可被Lua发现，在Lua拿到Entity类时，即可调用此方法。

而如果此方法已存在，如`Entity#addMovement`，实际上Lua一旦拿到Entity实例，即可直接调用此方法，但参数的传入必须符合JNLua的参数传入规则，
因此如果是一些基本的参数类型，如Double，Null，String等基本都可以传入，但特殊的参数，如具体的某个Java类，函数等，则需要通过`LuaValueProxy`作为桥梁传入。
