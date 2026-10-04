# Libbulletjme 双精度（Dp）探针

这里放着评估 Dp/DpMt 投放用的独立探针与基准。它们**不是**模组的一部分，不会被编译进 mod jar，
也不会被 Gradle 的常规任务碰到；只有需要复现文档里的实测数据时才手动编译运行。

配套文档：`docs/libbulletjme-double-precision-evaluation.md`（§3.3 的精度对比、§4.1 的吞吐、
§10 的复现实验）。

## 前置条件

- **JDK 21**。探针编译出的 class 文件是 major 65，系统 JDK 8 跑不起来。
  用 `$env:JAVA_HOME\bin\java.exe`（本机是 JBR 21）。
- 先构建一次项目自身的 Java/Kotlin 输出，探针只依赖它们：
  ```powershell
  .\gradlew.bat classes
  ```
  产物是 `build\classes\java\main`（内嵌的 jme3 / simsilica / jme3utilities 引擎树）与
  `build\classes\kotlin\main`（`cn.solarmoon.spark_core.*`）。**不需要** Minecraft 与 NeoForge 的依赖，
  探针只用 `com.jme3.bullet.*`、`com.jme3.math.*`、`com.simsilica.mathd.*`。

## 编译

```powershell
$javac = "$env:JAVA_HOME\bin\javac.exe"
$cp = "build\classes\java\main;build\classes\kotlin\main"
New-Item -ItemType Directory -Force build\dp-probe-out | Out-Null
& $javac -encoding UTF-8 -cp $cp -d build\dp-probe-out `
    tools\dp-probe\MixedModeTest.java tools\dp-probe\DistantTest.java `
    tools\dp-probe\BenchTest.java tools\dp-probe\BenchBig.java
```

运行时的 classpath 是 `build\dp-probe-out;$cp`（把探针自己的输出放在最前面）。

## 用法

四个探针都接受**一个参数：要加载的 `.dll` / `.so` / `.dylib` 的绝对路径**
（`System.load` 不接受相对路径）。本仓库自带的三档 native 就在
`src\main\resources\natives\<模块>\<平台>\<架构>\` 下。

```powershell
$java = "$env:JAVA_HOME\bin\java.exe"
$cp = "build\dp-probe-out;build\classes\java\main;build\classes\kotlin\main"
$dpmt = (Resolve-Path src\main\resources\natives\bullet_dpmt\windows\x86_64\bulletjme.dll).Path
$dp   = (Resolve-Path src\main\resources\natives\bullet_dp\windows\x86_64\bulletjme.dll).Path

# 同一份 class 在不同 native 上跑 Dp + float 入口（文档 §7.1/§7.2 的依据）
& $java -cp $cp MixedModeTest $dpmt
& $java -cp $cp MixedModeTest $dp

# 远距离落地精度对比（文档 §3.3）
& $java -cp $cp DistantTest $dpmt

# 吞吐（文档 §4.1），每个 native 跑 3 次取均值
& $java -cp $cp BenchTest $dpmt      # 800 动态刚体，600 tick
& $java -cp $cp BenchBig  $dpmt      # 4000 动态刚体，300 tick
```

想换成 Sp native 或 Maven Central 上的其它档，直接把对应文件路径传进去即可。

## 获取其它档位的构件

上游的发布矩阵见 `..\..\Libbulletjme\.github\workflows\push.yml`。Maven Central 的坐标形如：

```
https://repo1.maven.org/maven2/com/github/stephengold/Libbulletjme-<平台>/23.1.0/Libbulletjme-<平台>-23.1.0-<档位>.jar
```

- 平台：`Windows64`、`Linux64`、`Linux_ARM64`、`MacOSX64`、`MacOSX_ARM64`、`Android`
- 档位：`SpRelease`、`SpMtRelease`、`DpRelease`、`DpMtRelease`
- 构件内的路径：`native/<windows|linux|osx>/<x86_64|arm64>/<bulletjme.dll|libbulletjme.so|libbulletjme.dylib>`

> ⚠ 构件里 macOS 的平台段是 **`osx`**，而加载器与投放目录用的是 **`macos`**，
> 解包进 `src\main\resources\natives\` 时必须重命名，否则加载器取不到、会静默落到下一个候选。

## 脱离 jme3 Java 层直接问 native 自己

`stub\com\jme3\bullet\util\NativeLibrary.java` 是一个**手写的同名桩类**，只声明 `native` 方法、
外加一个 `main`。因为它的包名与类名和真实实现完全一致，排在 classpath 最前面时会顶掉项目里的实现，
于是可以在**完全不构建项目**的情况下打印 native 自己的档位信息：

```powershell
& $javac -encoding UTF-8 -d build\probe-out tools\dp-probe\stub\com\jme3\bullet\util\NativeLibrary.java
& $java -cp "build\probe-out" com.jme3.bullet.util.NativeLibrary $dpmt
```

输出：

```
  versionNumber     = 23.1.0
  isDoublePrecision = true
  isThreadSafe      = true
  isDebug           = false
  isQuickprof       = false
  countThreads      = 2          # 只有非 Mt 构件能打印出这一行
```

> ⚠ 已知限制：在 **Mt** 构件（`SpMtRelease` / `DpMtRelease`）上调用 `countThreads()` 时，
> native 会回调 `com.jme3.bullet.CollisionSpace` 等 Java 类，而 `build\probe-out` 里没有它们，
> 于是抛 `NoClassDefFoundError: com/jme3/bullet/CollisionSpace`。
> 上面四行仍然会正常打印（`isThreadSafe=true` 已经说明它是 Mt 档），
> 需要 `countThreads()` 的确切数字时改用带完整 classpath 的 `MixedModeTest`。
> 非 Mt 构件不受影响，`countThreads` 会正常打印为 `1`。

