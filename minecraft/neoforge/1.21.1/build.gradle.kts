val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version: String by project
val neo_version_range: String by project
val parchment_mappings_version: String by project
val parchment_minecraft_version: String by project
val mod_loader: String by project
val loader_version_range: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val mod_authors: String by project
val mod_license: String by project
val mod_credits: String by project
val mod_description: String by project
val mod_group_id: String by project
val build_name: String by project
val scf_version: String by project
val kff_version: String by project
val arrow_version: String by project
val graaljs_version: String by project

plugins {
    idea
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("net.neoforged.moddev") version "2.0.141"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

version = mod_version
group = "$mod_group_id.$mod_id"

base {
    archivesName.set("$build_name-$minecraft_version-$mod_loader")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.apply {
            add("-Xjvm-default=all")
            add("-Xcontext-parameters")
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
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

        create("clientAuth") {
            client()
            devLogin = true
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to neo_version,
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_credits" to mod_credits,
        "mod_description" to mod_description,
        "kff_version" to kff_version
    )
    inputs.properties(replaceProperties)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "meta.json")) {
        expand(replaceProperties)
    }
}


val core by configurations.creating
val externalLib by configurations.creating

externalLib.dependencies.configureEach {
    if (this is ModuleDependency) {
        exclude("org.jetbrains.kotlin")
    }
}

configurations {
    maybeCreate("additionalRuntimeClasspath").apply {
        extendsFrom(core)
        extendsFrom(externalLib)
    }
    jarJar.get().apply {
        extendsFrom(externalLib)
    }
    implementation.get().apply {
        extendsFrom(core)
        extendsFrom(externalLib)
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    core("thedarkcolour:kotlinforforge-neoforge:${kff_version}")

    externalLib(project(":api")) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    externalLib("io.github.nsk90:kstatemachine-jvm") {
        version {
            strictly("[0.34,)")
            prefer("0.34.2")
        }
    }

    externalLib("io.arrow-kt:arrow-core") {
        version {
            strictly(arrow_version)
        }
    }

    externalLib("io.arrow-kt:arrow-core-serialization") {
        version {
            strictly(arrow_version)
        }
    }

    externalLib("io.arrow-kt:arrow-fx-coroutines") {
        version {
            strictly(arrow_version)
        }
    }

    listOf(
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
    ).forEach {
        externalLib(it) {
            version {
                strictly(graaljs_version)
            }
        }
    }

    // 兼容
    implementation("maven.modrinth:jade:${property("jade_version")}")

    // 本地
    runtimeOnly(files(fileTree(mapOf("dir" to "mods", "includes" to listOf("*.jar"))))) // mods => 运行时依赖
}

repositories {
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
        name = "Kotori316 main"
        url = uri("https://maven.kotori316.com")
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

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

