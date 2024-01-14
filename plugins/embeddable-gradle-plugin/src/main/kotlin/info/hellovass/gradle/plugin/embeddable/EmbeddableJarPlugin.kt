@file:Suppress("unused", "UNUSED_VARIABLE")

package info.hellovass.gradle.plugin.embeddable

import info.hellovass.gradle.plugin.embeddable.extension.EmbeddableExtension
import info.hellovass.gradle.plugin.embeddable.tasks.JvmArgsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

class EmbeddableJarPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        val embeddable = extensions.create<EmbeddableExtension>("embeddable")

        val embedded = configurations.register("embedded") {
            isCanBeResolved = true
        }

        configurations.named("compileOnly") {
            extendsFrom(embedded.get())
        }

        afterEvaluate {
            dependencies {
                //
                add("compileOnly", files(embeddable.yourkitJar))
                //
                embedded.get()(embeddable.allocationInstrument.get())
            }
        }

        val jvmArgsTask = tasks.register<JvmArgsTask>("jvmArgs") {
            this.yourkitJar.set(embeddable.yourkitJar)
            this.yourkitAgent.set(embeddable.yourkitAgent)
            this.outputFile.set(buildDir.resolve("jvmArgs").resolve("jvmArgs.txt"))
        }

        jvmArgsTask.configure {
            dependsOn("assemble")
        }
    }
}