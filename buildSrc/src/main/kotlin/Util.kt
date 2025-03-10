import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import org.gradle.plugin.use.PluginDependency
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.Locale

/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

/**
 * Collector of imperative code.
 */
object Util {

    private val javadocIOcacheFile = File("javadoc-io.json")
    private val gson = Gson().newBuilder().setPrettyPrinting().create()
    private val mapType = object : TypeToken<MutableMap<String, Pair<URL, URL?>>>() { }.type

    @Suppress("UNCHECKED_CAST")
    private val javadocIO: MutableMap<String, Pair<URL, URL?>> = javadocIOcacheFile
        .takeIf(File::exists)
        ?.let { gson.fromJson(it.readText(), mapType) }
        ?: mutableMapOf()

    /**
     * If available, finds the URL of the documentation on javadoc.io for [dependency].
     *
     * @return a [Pair] with the URL as a first element, and the packageList URL as second element.
     */
    fun Project.fetchJavadocIOForDependency(dependency: Dependency): Pair<URL, URL>? = dependency
        .takeIf { it is ExternalDependency }
        ?.run {
            synchronized(javadocIO) {
                val size = javadocIO.size
                val descriptor = "$group/$name/$version"
                val javadocIOURLs = javadocIO.getOrPut(descriptor) {
                    logger.lifecycle("Checking javadoc.io for unknown dependency {}:{}:{}", group, name, version)
                    val urlString = "https://javadoc.io/doc/$descriptor"
                    val packageList = listOf("package-list", "element-list")
                        .map { URL("$urlString/$it") }
                        .firstOrNull { runCatching { it.openStream() }.isSuccess }
                    if (packageList == null) {
                        logger.lifecycle("javadoc.io has docs for {}:{}:{}! > {}", group, name, version, urlString)
                    }
                    URL(urlString) to packageList
                }
                if (javadocIO.size != size) {
                    logger.lifecycle("Caching javadoc.io information for {} at {}", descriptor, javadocIOURLs.first)
                    javadocIOcacheFile.writeText(gson.toJson(javadocIO.toSortedMap()))
                }
                javadocIOURLs.second?.let { javadocIOURLs.first to it }
            }
        }

    /**
     * Verifies that the generated shadow jar displays the help, and that SLF4J is not falling back to NOP.
     */
    fun Project.testShadowJar(jarFile: Provider<RegularFile>) = tasks.register<Exec>(
        "test${
        jarFile.get().asFile.nameWithoutExtension
            .removeSuffix("-all")
            .removePrefix("alchemist-")
            .capitalize()
            .replace(Regex("-([a-z])")) { it.groupValues[1].toUpperCase(Locale.US) }
            .replace("-", "")
        }ShadowJarOutput"
    ) {
        group = "Verification"
        description = "Verifies the terminal output correctness when printing the help via ${jarFile.get().asFile.name}"
        val javaExecutable = org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath
        val interceptOutput = ByteArrayOutputStream()
        val interceptError = ByteArrayOutputStream()
        standardOutput = interceptOutput
        errorOutput = interceptError
        isIgnoreExitValue = true
        doFirst {
            commandLine(javaExecutable, "-jar", jarFile.get().asFile.absolutePath, "--help")
        }
        doLast {
            val exit = executionResult.get().exitValue
            require(exit == 0) {
                val outputs = listOf(interceptOutput, interceptError).map { String(it.toByteArray(), Charsets.UTF_8) }
                outputs.forEach { text ->
                    for (illegalKeyword in listOf("SLF4J", "NOP")) {
                        require(illegalKeyword !in text) {
                            """
                            |$illegalKeyword found while printing the help. Complete output:
                            |$text
                            """.trimMargin()
                        }
                    }
                }
                """
                |Process '${commandLine.joinToString(" ")}' exited with $exit
                |Output:
                |${outputs[0]}
                |Error:
                |${outputs[0]}
                """.trimMargin()
            }
        }
    }

    /**
     * Directly accesses the plugin id.
     */
    val Provider<PluginDependency>.id: String get() = get().pluginId

    /**
     *  Check if the project contains at least one of the multiplatform most common sourceSets,
     *  if so, assume it is a multiplatform project.
     */
    val Project.isMultiplatform get() = listOf("common", "jvm", "js", "native").any {
        projectDir.resolve("src/${it}Main").exists()
    }
}
