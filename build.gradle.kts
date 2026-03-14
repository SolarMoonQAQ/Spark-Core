import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    id("java-library")
    id("maven-publish")
    id("idea")
    id("net.neoforged.moddev") version "2.0.115"
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.20"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

val mod_version: String by project
val mod_group_id: String by project
val mod_id: String by project
val build_name: String by project
val minecraft_version: String by project


tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}

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

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
    implementation("maven.modrinth:real-camera:0.6.1-beta-1.21")
    implementation("maven.modrinth:first-person-model:Sx5QD2SF")

    // 外部库 ------------------------------------------------------------------------------------------------------------
    implementation("cn.solarmoon:spark-core-graaljs-neoforge:1.21.1-1.0.0")?.let { jarJar(it) }
    additionalRuntimeClasspath("cn.solarmoon:spark-core-graaljs-neoforge:1.21.1-1.0.0")
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


