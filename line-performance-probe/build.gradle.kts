java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

plugins {
    java
    id("info.hellovass.embeddable")
}

embeddable {
    yourkitJar.set("/Applications/YourKit-Java-Profiler-2023.9.app/Contents/Resources/lib/yourkit.jar")
    yourkitAgent.set("/Applications/YourKit-Java-Profiler-2023.9.app/Contents/Resources/bin/mac/libyjpagent.dylib")
    allocationInstrument.set("com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.3.4")
}