plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.20" apply false
}

group = "info.hellovass"
version = "1.0.0-alpha.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

val clean by tasks.registering(Delete::class) {
    delete(rootProject.buildDir)
}