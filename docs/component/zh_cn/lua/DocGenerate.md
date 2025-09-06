## 自动生成您的Lua方法注解

---
<div style="text-align: center;">
    <i>注意：注释生成目前支持kotlin的.kt文件</i>
</div>

在[拓展Lua方法](LuaMethods.md)这一节中，可以知道通过核心拓展Lua可用方法的方式，一旦掌握了这一点，
那么就可以很轻松的将代码中的拓展方法自动化的去生成Lua的专有注解，这样您就可以在各种支持Lua格式的IDE中直接获取代码补全/提示的便利。

比如，我已经拓展了一个自定义方法如：
````
local entity = Entity:new()
entity:customMethod()
````
但此时lua中并没有注释，虽然可以调用，但有时总会遗忘可用的方法，那么您可以直接像这样写：
````
@LuaClass("Entity")
interface LuaEntity {
    /**
     * 自定义方法
     */
    fun customMethod()
}
````

然后，在您的`build.gradle`中添加这一段：
````
tasks.register('generateLuaDocs', JavaExec) {
    group = "build"
    description = "解析源码并生成 Lua 注释文件"
    mainClass = 'cn.solarmoon.spark_core.LuaDocsGenerator'
    classpath = sourceSets.main.runtimeClasspath
}
````
最后，在控制台中调用`gradlew generateLuaDocs`即可在项目的`resource/spark_modules/.docs/YOUR_MOD_ID`目录下生成您自己模组所拓展的方法注解，
并且如果代码中方法有KDoc注释，在注解中也会生成相应的注释，最后在游戏运行时，会将注解文件复制到游戏目录的拓展文件夹中，方便直接通过IDE打开使用。

而对于全局静态方法，您只要在您自己拓展的类的头部写上注释：`@LuaGlobal(methodNmae)`


