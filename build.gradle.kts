import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.6.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "1.3.1"
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.13"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
    updateSinceUntilBuild.set(false)
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

}


repositories {
    mavenCentral()
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.20")

    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.1.1")
    implementation("com.sun.codemodel:codemodel:2.6")


    implementation("javax.servlet:javax.servlet-api:3.0.1")
    implementation("org.eclipse.jetty:jetty-server:9.3.20.v20170531")
    implementation("org.eclipse.jetty:jetty-webapp:9.3.20.v20170531")
    implementation("org.eclipse.jetty:jetty-continuation:9.3.20.v20170531")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")

    implementation("org.apache.commons:commons-lang3:3.10")

    implementation("org.apache.commons:commons-text:1.8")

    implementation("org.jclarion:image4j:0.7")

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.20")
}

val fatJar = task("fatJar", type = Jar::class) {
    manifest {
        attributes["Implementation-Title"] = "Gradle Jar File Example"
        attributes["Implementation-Version"] = version
    }
    baseName = "${project.name}-all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    with(tasks["jar"] as CopySpec)
}

val webappDir = "src/main/webapp"
sourceSets {
    main {
        resources {
            srcDirs("$webappDir/dist", "src/main/resources")
        }
    }
}

(tasks.getByName("processResources") as ProcessResources).apply {
    dependsOn("buildAngular")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

task<Exec>("buildAngular") {
    dependsOn("installAngular")
    workingDir(webappDir)
    inputs.dir(webappDir)
    group = BasePlugin.BUILD_GROUP
    if (System.getProperty("os.name").toUpperCase().contains("WINDOWS")){
        commandLine("ng.cmd", "build")
    } else {
        commandLine("ng", "build")
    }
}

task<Exec>("installAngular") {
    workingDir(webappDir)
    inputs.dir(webappDir)
    group = BasePlugin.BUILD_GROUP
    if (System.getProperty("os.name").toUpperCase().contains("WINDOWS")){
        commandLine("npm.cmd", "install")
    } else {
        commandLine("npm", "install")
    }
}

