plugins {
    id("java-library")
    id("maven-publish")
    id("idea")
    id("net.neoforged.moddev") version "1.0.14"
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.20"
    id("com.vanniktech.maven.publish") version "0.34.0"
    // ★ Shadow：将 GraalJS 的 org.graalvm 包重定位，避免与 GraalVM JDK 内置模块冲突
    // 使用 8.3.x 以兼容 Gradle 8.9（9.x 要求 Gradle 9.0+）
    id("com.gradleup.shadow") version "8.3.11"
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val mod_version: String by project
val mod_group_id: String by project
val mod_id: String by project
val build_name: String by project
val minecraft_version: String by project

try {
    tasks.named<Wrapper>("wrapper") {
        distributionType = Wrapper.DistributionType.ALL
    }
} catch (e:Exception ) {}

version = mod_version
group = "${mod_group_id}.${mod_id}"

base {
    archivesName.set("${build_name}-${minecraft_version}-${property("mod_loader")}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

neoForge {
    version = project.property("neo_version") as String

    parchment {
        mappingsVersion = project.property("parchment_mappings_version") as String
        minecraftVersion = project.property("parchment_minecraft_version") as String
    }

    setAccessTransformers(project.files("src/main/resources/META-INF/accesstransformer.cfg"))

    interfaceInjectionData {
        from("src/main/resources/META-INF/spark_interfaces.json")
        publish(file("src/main/resources/META-INF/spark_interfaces.json"))
    }

    runs {
        create("client") {
            client()
            gameDirectory = project.file("run-client")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            server()
            gameDirectory = project.file("run-server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            data()
            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(project.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

configurations {
    runtimeClasspath.get().extendsFrom(configurations["additionalRuntimeClasspath"])
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "neo_version" to project.property("neo_version"),
        "neo_version_range" to project.property("neo_version_range"),
        "loader_version_range" to project.property("loader_version_range"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_license" to project.property("mod_license"),
        "mod_version" to project.property("mod_version"),
        "mod_authors" to project.property("mod_authors"),
        "mod_credits" to project.property("mod_credits"),
        "mod_description" to project.property("mod_description"),
        "kotlinforforge_version" to project.property("kotlinforforge_version")
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// ==========================================
// ShadowJar：将 GraalJS 包名重定位以解决与 GraalVM JDK 的模块冲突
// 当玩家使用 GraalVM 作为运行 JVM 时，JDK 自带的 org.graalvm.* 模块会与
// 内嵌的 GraalJS jar 发生模块名冲突。通过 Shadow relocate 将所有 org.graalvm
// 包移至 cn.solarmoon.spark_core.shadow.org.graalvm 下彻底消除冲突。
// ==========================================
val shadowPrefix = "cn.solarmoon.spark_core.shadow"
val graaljsForShadow by configurations.creating {
    isTransitive = false
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("slim")          // 原始 jar 改名为 slim（无 GraalJS 依赖）
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")              // shadowJar 作为最终分发的 mod jar
    dependsOn(tasks.jar)                   // 先等 slim jar 构建完成

    // 以 slim jar 为基底（包含主源码 + jarJar 依赖如 extlibs），再合并 GraalJS
    from(zipTree(tasks.jar.get().archiveFile))
    configurations = listOf(graaljsForShadow)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // 去除签名文件、module-info（含多版本 jar 中的），避免模块层冲突
    exclude(
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "module-info.class",
        "META-INF/versions/*/module-info.class"
    )
    mergeServiceFiles()

    // ★ 核心：将所有 GraalVM 相关包重定位到内部命名空间
    // org.graalvm.* — GraalVM SDK / Polyglot / Truffle 主包
    relocate("org.graalvm", "$shadowPrefix.org.graalvm")
    // com.oracle.svm.* — nativeimage 模块（与 GraalVM JDK 的 org.graalvm.nativeimage 冲突）
    relocate("com.oracle.svm", "$shadowPrefix.com.oracle.svm")
    // com.oracle.truffle.* — Truffle 语言框架内部包（与 GraalVM JDK 的 org.graalvm.truffle 冲突）
    relocate("com.oracle.truffle", "$shadowPrefix.com.oracle.truffle")
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}

tasks.register<JavaExec>("generateJSDocs") {
    group = "build"
    description = "解析源码并生成 TS 声明文件"
    mainClass.set("cn.solarmoon.spark_core.js.doc.TSDocsGenerator")
    classpath = sourceSets.main.get().runtimeClasspath
}

fun DependencyHandlerScope.additionalRuntimeClasspath(dep: Any) {
    configurations["additionalRuntimeClasspath"].dependencies.add(
        dependencies.create(dep)
    )
}

val graaljsVersion = property("graaljs_version").toString()
val graaljsJarJarVersionRange = "[${property("graaljs_min_version")},${property("graaljs_next_major_version")})"
val graaljsJarJarModules = listOf(
    "org.graalvm.polyglot:polyglot",
    "org.graalvm.js:js-language",
    "org.graalvm.regex:regex",
    "org.graalvm.shadowed:icu4j",
    "org.graalvm.shadowed:xz",
    "org.graalvm.truffle:truffle-api",
    "org.graalvm.truffle:truffle-compiler",
    "org.graalvm.truffle:truffle-runtime",
    "org.graalvm.sdk:collections",
    "org.graalvm.sdk:jniutils",
    "org.graalvm.sdk:nativebridge",
    "org.graalvm.sdk:nativeimage",
    "org.graalvm.sdk:word",
)

dependencies {
    // KotlinForForge
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kotlinforforge_version")}")

    // jei
    compileOnly("mezz.jei:jei-${property("minecraft_version")}-common-api:${property("jei_version")}")
    compileOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge-api:${property("jei_version")}")
    runtimeOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge:${property("jei_version")}")

    // 玉
    implementation("maven.modrinth:jade:${property("jade_version")}")

    // 兼容 ------------------------------------------------------------------------------------------------------------
    implementation("software.bernie.geckolib:geckolib-neoforge-${property("minecraft_version")}:${property("geckolib_version")}")
    implementation("mod.azure.azurelib:azurelib-neo-${property("minecraft_version")}:${property("azurelib_version")}")
    implementation("dev.kosmx.player-anim:player-animation-lib-forge:${property("player_animator_version")}")
    // 机械动力
    implementation("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:${property("ponder_version")}+mc${property("minecraft_version")}")
    implementation("dev.engine-room.flywheel:flywheel-neoforge-api-${property("minecraft_version")}:${property("flywheel_version")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${property("minecraft_version")}:${property("flywheel_version")}")
    implementation("com.tterrag.registrate:Registrate:${property("registrate_version")}")
    // 第一人称
    compileOnly("maven.modrinth:real-camera:0.7.4-beta-1.21.1")
    compileOnly("maven.modrinth:first-person-model:Sx5QD2SF")
    // 加速渲染
    compileOnly("maven.modrinth:acceleratedrendering:1.0.5-1.21.1-alpha")

    // 外部库 ------------------------------------------------------------------------------------------------------------
    // GraalJS：编译期用原始坐标；ShadowJar 通过 graaljsForShadow 配置合并并重定位；
    // 开发环境仍通过 additionalRuntimeClasspath 使用原始模块（classpath 不触发模块检核）。
    graaljsJarJarModules.forEach { module ->
        implementation(module) {
            version {
                strictly(graaljsJarJarVersionRange)
                prefer(graaljsVersion)
            }
        }
    }
    graaljsJarJarModules.forEach { module ->
        graaljsForShadow("$module:$graaljsVersion")
    }
    graaljsJarJarModules.forEach { module ->
        additionalRuntimeClasspath("$module:$graaljsVersion")
    }
    // 状态机
//    implementation("io.github.nsk90:kstatemachine-jvm:0.34.2")?.let { jarJar(it) }
//    additionalRuntimeClasspath("io.github.nsk90:kstatemachine-jvm:0.34.2")

    // 本地库 ------------------------------------------------------------------------------------------------------------
    implementation(files(fileTree(mapOf("dir" to "mods", "includes" to listOf("*.jar")))))

    // 编译用，暂时无需依赖
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    implementation(fileTree(mapOf("dir" to "extlibs", "includes" to listOf("*.jar"))))?.let { jarJar(it) }
    additionalRuntimeClasspath(fileTree(mapOf("dir" to "extlibs", "includes" to listOf("*.jar"))))
    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    mavenLocal()
    exclusiveContent {
        forRepository {
            mavenCentral()
        }
        filter {
            includeGroupByRegex("org\\.graalvm(\\..*)?")
        }
    }
    mavenCentral()

    maven {
        name = "Progwml6 maven"
        url = uri("https://dvs1.progwml6.com/files/maven/")
    }
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev")
    }
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "KosmX's maven"
        url = uri("https://maven.kosmx.dev/")
    }
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
    }
    maven {
        name = "tterrag maven"
        url = uri("https://maven.tterrag.com/")
    }
    maven {
        name = "Create Maven"
        url = uri("https://maven.createmod.net")
    }
    maven {
        name = "IThundxr Snapshots"
        url = uri("https://maven.ithundxr.dev/snapshots")
    }
    maven {
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        url = uri("https://maven.ryanliptak.com/")
    }
    maven {
        url = uri("https://maven.theillusivec4.top/")
    }
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroup("software.bernie.geckolib")
        }
    }
    maven {
        url = uri("https://maven.azuredoom.com/mods")
    }
    maven {
        url = uri("https://maven.pkg.github.com/SolarMoonQAQ/Spark-Core")
        credentials {
            username = System.getenv("GITMAVEN_USERNAME")
            password = System.getenv("SolarMoonCore_TOKEN")
        }
    }
    flatDir {
        dirs("libs")
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("io.github.solarmoonqaq", "${property("artifact_id")}-${property("mod_loader")}", "${property("mod_version")}")

    pom {
        name.set("${property("mod_name")}")
        description.set("${property("mod_description")}")
        url.set("${property("mod_url")}")
        inceptionYear.set("2025")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("SolarMoonQAQ")
                name.set("曦月")
                email.set("3213382746@qq.com")
                url.set("https://github.com/SolarMoonQAQ")
            }
        }
        scm {
            connection.set("scm:git:${property("mod_url")}.git")
            developerConnection.set("scm:git:${property("mod_url")}.git")
            url.set("${property("mod_url")}")
        }
    }
}


