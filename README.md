Android Lint Performance Probe
===

This is a simple tool to help pinpoint performance bottlenecks in individual
[Android Lint](https://developer.android.com/studio/write/lint) checks. It uses Java byte code instrumentation to
collect and report performance statistics per detector for a Lint analysis invoked from Gradle. It relies on
[YourKit Probes](https://www.yourkit.com/docs/java/help/probe_class.jsp) to do the byte code instrumentation part,
although in principle alternative instrumentation agents could be used.

This tool was involved in the 2x-level Lint performance improvements that landed with the Android Studio 3.3 release;
there's a [blog post](https://link.medium.com/ppyrkiQEPV) with more details.


Getting started
---

This tool requires a valid [YourKit](https://www.yourkit.com/download/) installation. YourKit is a paid profiler,
although trial versions are available.

Once YourKit is installed, edit the properties in `gradle.properties` to point to the corresponding files in your
YourKit installation. You will need to set both the `yourkitJar` property and the `yourkitAgent` property.

Next, run
```
$ ./gradlew jvmArgs
```
to compile the tool and print out the JVM arguments needed to instrument Lint. Here's an example of what
those JVM arguments will look like:

```
-agentpath:/opt/yourkit/bin/linux-x86-64/libyjpagent.so=disableall,probebootclasspath=/path/to/lint-performance-probe/build/libs/lint-performance-probe.jar,probe_on=com.android.tools.probes.LintDetectorStats
```

Add the generated arguments to your Gradle JVM arguments in an Android project. (See [Gradle
documentation](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
for how to set the `org.gradle.jvmargs` property.)

If you are using Android Gradle Plugin 7.0 or higher, you may also need to force Lint to run in-process. You can put the following line in your app's `gradle.properties` file.
```
android.experimental.runLintInProcess=true # Needed for AGP 7+
```

Finally, invoke Lint from Gradle as you normally do. Once Lint finishes,
performance statistics for each Lint detector should be printed to the console.
Here's some sample output:
```
$ ./gradlew lintDebug --no-daemon -Dorg.gradle.jvmargs="..."

BUILD SUCCESSFUL in 10s
15 actionable tasks: 1 executed, 14 up-to-date

Lint detector performance stats:
                                     total           self          calls
                 LintDriver        3709 ms        1302 ms           2821
TopDownAnalyzerFacadeForJVM        2121 ms        2121 ms              6
               IconDetector          81 ms          81 ms            257
           OverdrawDetector          51 ms          51 ms             36
     InvalidPackageDetector          34 ms          34 ms          51744
             GradleDetector          20 ms          20 ms             94
       LocaleFolderDetector          11 ms          11 ms            986
         RestrictToDetector          11 ms          11 ms             19
                ApiDetector          11 ms          11 ms           1255
    PrivateResourceDetector          10 ms          10 ms            422
...
```

Memory allocation instrumentation
---

The tool also supports measuring memory allocations through the use of an
[allocation instrumentation agent](https://github.com/google/allocation-instrumenter). To enable this, generate the JVM
arguments with this command instead:
```
$ ./gradlew jvmArgs -Pallocations
```

Here's some sample output:
```
BUILD SUCCESSFUL in 22s
15 actionable tasks: 1 executed, 14 up-to-date

Lint detector performance stats:
                                     total           self          calls
                 LintDriver        1473 MB         589 MB           2821
TopDownAnalyzerFacadeForJVM         770 MB         770 MB              6
     AppIndexingApiDetector          67 MB          67 MB              8
                ApiDetector          13 MB          13 MB           1255
               IconDetector          11 MB          11 MB            257
         RestrictToDetector           8 MB           8 MB             19
    PrivateResourceDetector           3 MB           3 MB            422
                RtlDetector           2 MB           2 MB             10
             GradleDetector           2 MB           2 MB             94
           OverdrawDetector           1 MB           1 MB             36
```


Troubleshooting
---

- If you get strange error messages from the Gradle JVM, double check that the JVM arguments are formatted
  and quoted properly. Quoting may be needed if there are spaces in the file paths, for example. Similarly,
  be aware that typos in the JVM arguments may cause YourKit to silently ignore the probe.

- If the JVM arguments have been applied properly but you still don't see performance stats output, it's worth checking
  to see if YourKit has reported any errors---especially if you've made code changes to the probe. To do this, run
  `java` with the JVM arguments generated by this tool. YourKit should print a line to standard error
  like this.
  ```
  [YourKit Java Profiler 2018.04-b88] Log file: /path/to/home/.yjp/log/java-191039.log
  ```
  Open that log file to see if there were any errors reported. For example, if you added a malformed regular
  expression to the `@MethodPattern` annotation in `LintDetectorStats.java`, you might see a line like this.
  ```
  Error: probe class com.android.tools.probes.LintDetectorStats$WrapDetectors: class name pattern is invalid: com.example.lint.checks..*Detector
  ```
  Conversely, it's a _good_ sign if you see a line like this.
  ```
  Registered: com.android.tools.probes.LintDetectorStats
  ```

- If you make code changes to the tool, be sure to run `./gradlew assemble` in order to rebuild the probe.
  Also, be sure to start a new Gradle daemon for Lint; otherwise the old version of the tool could still be in use.
  This is easy to do by using the `--no-daemon` Gradle flag.

- The tool may break after changes to Lint class/package names.
  Please file an issue if the tool stops working for you after an AGP udpate.


Notes and tips
---

- The tool will print out three columns for each detector:
  - `total` shows the total amount of time spent within any method in that detector
  - `self` is similar to `total`, but it excludes any time spent in other instrumented callees
  - `calls` shows the number of calls to any method in that detector

- In addition to individual Lint detectors, the tool also instruments `LintDriver` and `TopDownAnalyzerFacadeForJVM`.
  `LintDriver` drives the entire Lint analysis, so it will help show the total time spent in Lint.
  `TopDownAnalyzerFacadeForJVM` is essentially the entry point into the Kotlin compiler, so it is a good proxy for
  how much time is spent upfront analyzing a Kotlin module before Lint detectors can start.

- To include your own custom Lint checks in the output of the tool, you can add to the list of regular expressions
  in `LintDetectorStats.java`.

- Be aware that caching effects may distort the performance stats for individual detectors.
  For example, the first Lint check to run might get the blame for the initial cache misses when resolving
  calls, types, etc.
