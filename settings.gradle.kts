@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven("https://nexus.xmxdev.com/repository/maven-public/")
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {

    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()
        google()
    }
}

include("line-performance-probe")
includeBuild("./plugins")