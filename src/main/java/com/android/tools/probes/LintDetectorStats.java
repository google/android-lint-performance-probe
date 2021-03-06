// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.android.tools.probes;

import com.yourkit.probes.ClassRef;
import com.yourkit.probes.MethodPattern;
import com.yourkit.probes.OnEnterResult;

@SuppressWarnings("unused") // Used by reflection from YourKit.
public class LintDetectorStats {
    private static final PerformanceStats perfStats = new PerformanceStats();

    @MethodPattern({
            "com.android.tools.lint.client.api.LintDriver:*(*)",
            "org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM:*(*)",
            "com.android.tools.lint.checks.*Detector:*(*)"
    })
    public static class WrapDetectors {

        public static PerformanceStats.Tracepoint onEnter(@ClassRef Class<?> clazz) {
            return perfStats.enter(clazz.getName());
        }

        public static void onExit(
                @ClassRef Class<?> clazz,
                @OnEnterResult PerformanceStats.Tracepoint parentTracepoint) {
            perfStats.exit(clazz.getName(), parentTracepoint);
        }
    }

    @MethodPattern({
        "com.android.tools.lint.LintCoreApplicationEnvironment:disposeApplicationEnvironment()", // AGP 3.6 and earlier.
        "com.android.tools.lint.UastEnvironment:disposeApplicationEnvironment()" // AGP 4.0 and later.
    })
    public static class WrapAppEnv {

        public static void onExit() {
            perfStats.dumpStats();
            System.out.println("Clearing stats");
            perfStats.clear();
        }
    }
}
