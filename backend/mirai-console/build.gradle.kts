/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("UnusedImport")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration
import java.time.Instant

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java")
    `maven-publish`
    id("com.jfrog.bintray")
    id("net.mamoe.kotlin-jvm-blocking-bridge")
}

version = Versions.console
description = "Mirai Console Backend"

kotlin {
    explicitApiWarning()
}

dependencies {
    compileAndTestRuntime(`mirai-core-api`)
    compileAndTestRuntime(`mirai-core-utils`)
    compileAndTestRuntime(`kotlin-stdlib-jdk8`)

    compileAndTestRuntime(`kotlinx-atomicfu`)
    compileAndTestRuntime(`kotlinx-coroutines-core`)
    compileAndTestRuntime(`kotlinx-serialization-core`)
    compileAndTestRuntime(`kotlinx-serialization-json`)
    compileAndTestRuntime(`kotlin-reflect`)

    implementation(project(":mirai-console-compiler-annotations"))

    smartImplementation(yamlkt)
    smartImplementation(`jetbrains-annotations`)
    smartImplementation(`caller-finder`)
    smartApi(`kotlinx-coroutines-jdk8`)

    testApi(`mirai-core`)
    testApi(`kotlin-stdlib-jdk8`)
}

tasks {
    val compileKotlin by getting {}

    register("fillBuildConstants") {
        group = "mirai"
        doLast {
            (compileKotlin as KotlinCompile).source.filter { it.name == "MiraiConsoleBuildConstants.kt" }.single()
                .let { file ->
                    file.writeText(
                        file.readText()
                            .replace(
                                Regex("""val buildDate: Instant = Instant.ofEpochSecond\(.*\)""")
                            ) {
                                """val buildDate: Instant = Instant.ofEpochSecond(${
                                    Instant.now().epochSecond
                                })"""
                            }
                            .replace(
                                Regex("""const val versionConst:\s+String\s+=\s+".*"""")
                            ) { """const val versionConst: String = "${project.version}"""" }
                    )
                }
        }
    }
}

configurePublishing("mirai-console")

setupTesterPluginSources()

fun Project.setupTesterPluginSources() {
    val testers = 2
    val taskNames = Array(testers + 1) { index ->
        if (index == testers) {
            "jarAutoEnd"
        } else {
            "jarTesterPlugin$index"
        }
    }
    sourceSets {
        val baseDir = project.projectDir.resolve("tester-plugins")
        fun newTester(name: String, index: Int) = create(name) {
            val consoleMain = this@sourceSets.getByName("main")
            val consoleTest = this@sourceSets.getByName("test")
            // Only need compile classpath
            compileClasspath += consoleMain.output
            compileClasspath += consoleMain.compileClasspath

            compileClasspath += consoleTest.output
            compileClasspath += consoleTest.compileClasspath

            resources.setSrcDirs(listOf(baseDir.resolve(name)))
            java.setSrcDirs(listOf(baseDir.resolve(name)))

        }.also { src ->

            project.kotlin.target.compilations.run {
                maybeCreate(src.name).let { ksrc ->
                    ksrc.associateWith(getByName("main"))
                    ksrc.source(src)
                    ksrc.javaSourceSet = src
                    src.compiledBy(ksrc.compileKotlinTaskName)
                }
            }

            tasks.register(taskNames[index], Jar::class.java) {
                from(src.output)
                archiveBaseName.set(name)
            }
        }
        repeat(testers) { index ->
            newTester("tester-plugin-${index + 1}", index)
        }
        newTester("AutoEnd", testers)
    }

    afterEvaluate {
        tasks.register("runTerminalDaemon", JavaExec::class.java) {
            dependsOn(*taskNames)

            val terminal = project(":mirai-console-terminal")
            classpath = terminal.configurations["testRuntimeClasspath"]
            classpath += project.sourceSets.getByName("main").output // console
            classpath += project.sourceSets.getByName("test").output // console
            classpath += terminal.sourceSets.getByName("main").output
            main = "net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader"

            val sandbox = project.buildDir.resolve("runTerminalDaemon")
            workingDir = sandbox
            doFirst {
                sandbox.mkdirs()
                val plugins = sandbox.resolve("plugins")
                plugins.walk().filter { it.isFile }.forEach { it.delete() }
                taskNames.map { project.tasks.getByPath(it) }.forEach { task ->
                    task.outputs.files.forEach { it.copyTo(plugins.resolve(it.name), true) }
                }
            }
            timeout.set(Duration.ofMinutes(5))
        }
        tasks.getByName("check").dependsOn("runTerminalDaemon")
    }
}
