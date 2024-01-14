@file:Suppress(
    "SpellCheckingInspection",
    "VulnerableLibrariesLocal"
)

val yourkitJar: String by project
val yourkitAgent: String by project

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

val hellovass: Configuration by configurations.creating
hellovass.isCanBeResolved = true

configurations {
    compileOnly {
        extendsFrom(hellovass)
    }
}

dependencies {
    // Look for yourkit.jar, which contains the probe APIs.
    if (!file(yourkitJar).exists()) {
        throw InvalidUserDataException(
            """\
            File not found: $yourkitJar
            Please edit gradle.properties to point to a valid yourkit.jar file.\
        """.trimIndent()
        )
    }

    compileOnly(files(yourkitJar))
    hellovass("com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.3.0")
}


val jvmArgs by tasks.registering {

    dependsOn("assemble")

    doLast {

        val jvmArgs = mutableListOf<String>()

        // Add the allocation instrumentation agent if -Pallocations argument used.
        if (project.hasProperty("allocations")) {
            logger.quiet("Note: using the allocation instrumentation agent (-Pallocations)")
            val allocationsAgent = hellovass.find { file ->
                file.name.contains("java-allocation-instrumenter")
            }
            jvmArgs += "-javaagent:${allocationsAgent}"
        }

        // Add the YourKit instrumentation agent.
        val agent = file(yourkitAgent)
        if (!agent.exists()) {
            throw InvalidUserDataException(
                """\
                File not found: $agent
                Please edit gradle.properties to point to a valid YourKit agent.\
            """.trimIndent()
            )
        }
        val agentArgs = listOf(
            "disableall", // Disables all built-in YourKit instrumentation.
            "probebootclasspath=${tasks.withType<Jar>().single().outputs.files.files.first()}",
            "probe_on=com.android.tools.probes.LintDetectorStats"
        )
        jvmArgs += "-agentpath:${agent}=${agentArgs.joinToString(",")}"

        // Print out results.
        println(
            """
            |***********************************************************************************
            |Please use the following JVM arguments to instrument a Lint invocation from Gradle.
            |Be careful to quote the arguments as needed.
            |
            |${jvmArgs.joinToString("\n\n")}
            |
            |Instrumentation results will be printed to console after the Lint analysis is done.
            |***********************************************************************************
        """.trimIndent()
        )
    }
}