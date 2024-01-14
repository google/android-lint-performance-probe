@file:Suppress("SpellCheckingInspection")

package info.hellovass.gradle.plugin.embeddable.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withType
import java.io.File

abstract class JvmArgsTask : DefaultTask() {

    @get:Input
    abstract val yourkitJar: Property<String>

    @get:Input
    abstract val yourkitAgent: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run(): Unit = project.run {

        val jvmArgs = mutableListOf<String>()

        if (hasProperty("allocations")) {
            val allocationInstrumenterJar = getAllocationInstrumenterJar(configurations.getByName("embedded"))
            jvmArgs += "-javaagent:${allocationInstrumenterJar}"
        }

        val agent = file(yourkitAgent.get())

        val agentArgs = listOf(
            "disableall",
            "probebootclasspath=${getPerformanceProbeClasspath()}",
            "probe_on=com.android.tools.probes.LintDetectorStats"
        )

        jvmArgs += "-agentpath:${agent}=${agentArgs.joinToString(",")}"

        outputFile.get().asFile.writeText(jvmArgs.joinToString("\n"))
    }

    /**
     * Returns the path to the probebootclasspath jar.
     */
    private fun Project.getPerformanceProbeClasspath(): File? {
        return tasks.withType<Jar>().single().outputs.files.single()
    }

    /**
     * Returns the path to the java-allocation-instrumenter jar.
     */
    private fun getAllocationInstrumenterJar(embedded: Configuration): File? {
        return embedded.find { file -> file.name.contains("java-allocation-instrumenter") }
    }
}
