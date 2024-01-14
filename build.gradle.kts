plugins{
    id("info.hellovass.embeddable") version "1.0.0-alpha.0" apply false
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