plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // Gradle
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        register("info.hellovass.embeddable") {
            id = name
            implementationClass = "info.hellovass.gradle.plugin.embeddable.EmbeddableJarPlugin"
        }
    }
}