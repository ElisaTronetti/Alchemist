/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

import Libs.alchemist
import Libs.incarnation
import Util.fetchJavadocIOForDependency
import Util.id
import Util.testShadowJar
import Util.isMultiplatform
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.danilopianini.gradle.mavencentral.JavadocJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.java.qa)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.multiJvmTesting)
    alias(libs.plugins.publishOnCentral)
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.taskTree)
    alias(libs.plugins.hugo)
}

allprojects {

    with(rootProject.libs.plugins) {
        if (project.isMultiplatform) {
            apply(plugin = kotlin.multiplatform.id)
        } else {
            apply(plugin = kotlin.jvm.id)
        }
        apply(plugin = dokka.id)
        apply(plugin = gitSemVer.id)
        apply(plugin = java.qa.id)
        apply(plugin = multiJvmTesting.id)
        apply(plugin = kotlin.qa.id)
        apply(plugin = publishOnCentral.id)
        apply(plugin = shadowJar.id)
        apply(plugin = taskTree.id)
    }

    multiJvm {
        jvmVersionForCompilation.set(11)
        maximumSupportedJvmVersion.set(latestJava)
    }

    repositories {
        google()
        mavenCentral()
        // for tornadofx 2.0.0 snapshot release
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
            content {
                includeGroup("no.tornado")
            }
        }
    }

    // JVM PROJECTS CONFIGURATIONS

    if (!project.isMultiplatform) {
        dependencies {
            with(rootProject.libs) {
                compileOnly(spotbugs.annotations)
                implementation(resourceloader)
                implementation(slf4j)
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("reflect"))
                testCompileOnly(spotbugs.annotations)
                // Test implementation: JUnit 5 + Kotest + Mockito + Mockito-Kt + Alchemist testing tooling
                testImplementation(bundles.testing.compile)
                testImplementation(alchemist("test"))
                // Test runtime: Junit engine
                testRuntimeOnly(bundles.testing.runtimeOnly)
                // executable jar packaging
            }
            if ("incarnation" in project.name) {
                runtimeOnly(rootProject)
            }
        }

        tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
            dokkaSourceSets.configureEach {
                jdkVersion.set(multiJvm.jvmVersionForCompilation)
                listOf("kotlin", "java")
                    .map { "src/main/$it" }
                    .map { it to File(projectDir, it) }
                    .toMap()
                    .filterValues { it.exists() }
                    .forEach { (path, file) ->
                        sourceLink {
                            localDirectory.set(file)
                            val project = if (project == rootProject) "" else project.name
                            val url = "https://github.com/AlchemistSimulator/Alchemist/blob/master/$project/$path"
                            remoteUrl.set(uri(url).toURL())
                            remoteLineSuffix.set("#L")
                        }
                    }
                configurations.implementation.get().dependencies.forEach { dep ->
                    val javadocIOURLs = fetchJavadocIOForDependency(dep)
                    if (javadocIOURLs != null) {
                        val (javadoc, packageList) = javadocIOURLs
                        externalDocumentationLink {
                            url.set(javadoc)
                            packageListUrl.set(packageList)
                        }
                    }
                }
            }
            failOnWarning.set(true)
        }
    }

    // MULTIPLATFORM PROJECTS CONFIGURATIONS

    if (project.isMultiplatform) {
        tasks.dokkaJavadoc {
            enabled = false
        }
        tasks.withType<JavadocJar>().configureEach {
            val dokka = tasks.dokkaHtml.get()
            dependsOn(dokka)
            from(dokka.outputDirectory)
        }
        publishing {
            publications {
                publications.withType<MavenPublication>().configureEach {
                    if ("OSSRH" !in name) {
                        artifact(tasks.javadocJar)
                    }
                }
            }
        }
    }

    // COMPILE

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xjvm-default=all", // Enable default methods in Kt interfaces
                // Context receivers temporarily disabled, as they are unsupported in Kotlin script
                // "-Xcontext-receivers", // Enable context receivers
            )
        }
    }

    // TEST AND COVERAGE

    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed", "standardError")
            exceptionFormat = TestExceptionFormat.FULL
        }
        useJUnitPlatform()
    }

    // CODE QUALITY

    javaQA {
        checkstyle {
            additionalConfiguration.set(
                """
                <module name="RegexpSingleline">
                    <property name="severity" value="error" />
                    <property name="format" value="Math\s*\.\s*random\s*\(\s*\)" />
                    <property name="fileExtensions" value="java,xtend,scala,kt" />
                    <property name="message"
                              value="Don't use Math.random() inside Alchemist. Breaks stuff." />
                </module>
                <module name="RegexpSingleline">
                    <property name="severity" value="error" />
                    <property name="format" value="class\s*\.\s*forName\s*\(" />
                    <property name="fileExtensions" value="java,xtend,scala,kt" />
                    <property name="message"
                              value="Use the library to load classes and resources. Breaks grid otherwise." />
                </module>
                <module name="RegexpSingleline">
                    <property name="severity" value="error" />
                    <property name="format" value="class\s*\.\s*getResource" />
                    <property name="fileExtensions" value="java,xtend,scala,kt" />
                    <property name="message"
                              value="Use the library to load classes and resources. Breaks grid otherwise." />
                </module>
                <module name="RegexpSingleline">
                    <property name="severity" value="error" />
                    <property name="format" value="class\s*\.\s*getClassLoader" />
                    <property name="fileExtensions" value="java,xtend,scala,kt" />
                    <property name="message"
                              value="Use the library to load classes and resources. Breaks grid otherwise." />
                </module>
                <module name="RegexpSingleline">
                    <property name="severity" value="warning" />
                    <property name="format" value="@author" />
                    <property name="fileExtensions" value="java,xtend,scala,kt" />
                    <property name="message"
                              value="Do not use @author. Changes and authors are tracked by the content manager." />
                </module>
                """.trimIndent()
            )
            additionalSuppressions.set(
                """
                <suppress files=".*[\\/]expressions[\\/]parser[\\/].*" checks=".*"/>
                <suppress files=".*[\\/]biochemistrydsl[\\/].*" checks=".*"/>
                """.trimIndent()
            )
        }
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports {
            create("html") { enabled = true }
        }
    }

    // PUBLISHING

    tasks.withType<Javadoc>().configureEach {
        // Disable Javadoc, use Dokka.
        enabled = false
    }

    if (System.getenv("CI") == true.toString()) {
        signing {
            val signingKey: String? by project
            val signingPassword: String? by project
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
    }

    group = "it.unibo.alchemist"
    val repoSlug = "AlchemistSimulator/Alchemist"
    publishOnCentral {
        projectDescription.set(extra["projectDescription"].toString())
        projectLongName.set(extra["projectLongName"].toString())
        licenseName.set("GPL 3.0 with linking exception")
        licenseUrl.set("https://github.com/$repoSlug/blob/develop/LICENSE.md")
        scmConnection.set("git:git@github.com:$repoSlug.git")
        repository("https://maven.pkg.github.com/${repoSlug.toLowerCase()}") {
            user.set("DanySK")
            password.set(System.getenv("GITHUB_TOKEN"))
        }
    }
    publishing.publications.withType<MavenPublication>().configureEach {
        pom {
            developers {
                developer {
                    name.set("Danilo Pianini")
                    email.set("danilo.pianini@unibo.it")
                    url.set("http://www.danilopianini.org")
                    roles.set(mutableSetOf("architect", "developer"))
                }
            }
        }
    }

    // Shadow Jar
    tasks.withType<ShadowJar> {
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to "Alchemist",
                    "Implementation-Version" to rootProject.version,
                    "Main-Class" to "it.unibo.alchemist.Alchemist",
                    "Automatic-Module-Name" to "it.unibo.alchemist"
                )
            )
        }
        exclude(
            "ant_tasks/",
            "about_files/",
            "help/about/",
            "build",
            ".gradle",
            "build.gradle",
            "gradle",
            "gradlew.bat",
            "gradlew",
        )
        isZip64 = true
        mergeServiceFiles()
        destinationDirectory.set(file("${rootProject.buildDir}/shadow"))
        if ("full" in project.name || "incarnation" in project.name || project == rootProject) {
            // Run the jar and check the output
            val testShadowJar = testShadowJar(archiveFile)
            testShadowJar.get().dependsOn(this)
            this.finalizedBy(testShadowJar)
        }
    }
}

/*
 * Root project additional configuration
 */
evaluationDependsOnChildren()

dependencies {
    // Depend on subprojects whose presence is necessary to run
    listOf("api", "engine", "loading").forEach { api(alchemist(it)) } // Execution requirements
    with(libs.apache.commons) {
        implementation(cli)
        implementation(io)
        implementation(lang3)
    }
    implementation(libs.apache.commons.cli)
    implementation(libs.guava)
    implementation(libs.logback)
    testRuntimeOnly(incarnation("protelis"))
    testRuntimeOnly(incarnation("sapere"))
    testRuntimeOnly(incarnation("biochemistry"))
    testRuntimeOnly(alchemist("cognitive-agents"))
    testRuntimeOnly(alchemist("physics"))
}

// WEBSITE

val websiteDir = File(buildDir, "website")

hugo {
    version = Regex("gohugoio/hugo@v([\\.\\-\\+\\w]+)")
        .find(file("deps-utils/action.yml").readText())!!.groups[1]!!.value
}

tasks.hugoBuild {
    outputDirectory = websiteDir
}

tasks {
    dokkaJavadocCollector {
        removeChildTasks(subprojects.filter { it.isMultiplatform })
    }

    mapOf("javadoc" to dokkaJavadocCollector, "kdoc" to dokkaHtmlCollector)
        .mapValues { it.value.get() }
        .forEach { (folder, task) ->
            hugoBuild.get().dependsOn(task)
            val copyTask = register<Copy>("copy${folder.capitalizeAsciiOnly()}IntoWebsite") {
                from(task.outputDirectory)
                into(File(websiteDir, "reference/$folder"))
            }
            hugoBuild.get().finalizedBy(copyTask)
        }
    register("injectVersionInWebsite") {
        val index = File(websiteDir, "index.html")
        if (!index.exists()) {
            println("${index.absolutePath} does not exist")
            dependsOn(hugoBuild.get())
        }
        doLast {
            require(index.exists()) {
                "file ${index.absolutePath} existed during configuration, but has been deleted."
            }
            val version = project.version.toString()
            index.parentFile.walkTopDown()
                .filter { it.isFile && it.extension.matches(Regex("html?", RegexOption.IGNORE_CASE)) }
                .filterNot { it.path.contains(Regex("${File.separator}(kdoc|javadoc)${File.separator}")) }
                .forEach { page ->
                    val text = page.readText()
                    val devTag = "!development preview!"
                    if (text.contains(devTag)) {
                        page.writeText(text.replace(devTag, version))
                    } else {
                        if (!text.contains(version)) {
                            logger.warn("Could not inject version $version into ${page.path}")
                        }
                    }
                }
        }
    }
}
