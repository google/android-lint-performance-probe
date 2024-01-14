@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://nexus.xmxdev.com/repository/maven-public/")
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {

    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenLocal()
        maven("https://nexus.xmxdev.com/repository/maven-public/")
        google()
        mavenCentral()
    }
}

include("embeddable-gradle-plugin")
