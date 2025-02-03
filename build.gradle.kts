import com.github.gradle.node.npm.task.NpmTask
import groovy.xml.dom.DOMCategory.attributes
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    id("com.github.node-gradle.node") version "7.1.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

node {
    download.set(true)       // Automatically download and use node
    version.set("18.19.0")   // Specify the Node.js version you want
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        plugins(
            providers.gradleProperty("platformPlugins")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) })

        instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jclarion:image4j:0.7")
    implementation("com.github.weisj:jsvg:1.7.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")
    implementation("org.apache.commons:commons-lang3:3.11")
    implementation("org.apache.commons:commons-text:1.10")

    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.2.2")
    implementation("com.sun.codemodel:codemodel:2.6")

    implementation("javax.servlet:javax.servlet-api:3.0.1")
    implementation("org.eclipse.jetty:jetty-server:9.4.57.v20241219")
    implementation("org.eclipse.jetty:jetty-webapp:9.4.57.v20241219")
    implementation("org.eclipse.jetty:jetty-continuation:9.4.57.v20241219")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

    implementation("org.snakeyaml:snakeyaml-engine:2.7")

    implementation("org.graalvm.js:js:22.3.2")
    implementation("org.graalvm.truffle:truffle-api:22.3.2")
    implementation("org.graalvm.js:js-scriptengine:22.3.2")
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}

val generateManifestFile by tasks.registering {
    val outputDir = layout.buildDirectory.dir("tmp/generateManifest")
    val manifestFile = outputDir.map { it.file("MANIFEST.MF") }

    // Declare the output so Gradle knows it's produced by this task
    outputs.file(manifestFile)

    doLast {
        // Create the directory if it doesn't exist
        manifestFile.get().asFile.parentFile.mkdirs()

        // Write the content
        manifestFile.get().asFile.writeText(
            // Minimal manifest content; modify as needed
            """
            Manifest-Version: 1.0
            Implementation-Title: XME.digital plugin
            Implementation-Version: ${project.version}
            """.trimIndent()
        )
    }
}

tasks.shadowJar {
    dependsOn(generateManifestFile)

    // Set the output JAR name to "my-project-all-1.0.0.jar"
    archiveBaseName.set(project.name + "-all")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    // Ensure the JAR includes a manifest
    manifest {
        attributes(
            "Main-Class" to "com.example.MainKt",  // Update to your main class if needed
            "Implementation-Title" to "XME.digital plugin",
            "Implementation-Version" to project.version
        )
    }

    // Optionally avoid including duplicates
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

tasks.register<NpmTask>("buildAngular") {
    dependsOn("installAngular")
    workingDir.set(File(webappDir))
    inputs.dir(webappDir)
    group = BasePlugin.BUILD_GROUP
    args = listOf("run", "build")
}

tasks.register<NpmTask>("installAngular") {
    workingDir.set(File(webappDir))
    inputs.dir(webappDir)
    group = BasePlugin.BUILD_GROUP
    args = listOf("install")
}

