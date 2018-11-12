# Gradle Profiler

A tool to automate the gathering of profiling and benchmarking information for Gradle builds.

Profiling information can be captured using several different tools:

- Using a [Gradle build scan](https://gradle.com)
- Using [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html).
- Using [YourKit](https://www.yourkit.com) profiler.
- Using [Honest Profiler](https://github.com/RichardWarburton/honest-profiler)
- Using [Java flight recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm#JFRUH170) built into the Oracle JVM
- Using [Linux Perf](https://perf.wiki.kernel.org)
- Producing [Chrome Trace](https://www.chromium.org/developers/how-tos/trace-event-profiling-tool) output.

## Installing

First, build and install the `gradle-profiler` app using:

    > ./gradlew installDist

This will install the executable into `./build/install/gradle-profiler/bin`. The examples below assume that you add this location to your PATH or create a `gradle-profiler` alias for it.

## Benchmarking a build

Benchmarking simply records the time it takes to execute your build several times and calculates a mean and standard error for it. It has zero impact on the execution time, so it
is ideal for making before/after comparisons for new Gradle versions or changes to your build.

Run the app using:

    > gradle-profiler --benchmark --project-dir <root-dir-of-build> <task>...

Where `<root-dir-of-build>` is the directory containing the build to be benchmarked, and `<task>` is the name of the task to run,
exactly as you would use for the `gradle` command.

Results will be written to a file called `profile-out/benchmark.html` and `profile-out/benchmark.csv`.

When the profiler runs the build, it will use the tasks you specified. The profiler will use the default
Gradle version, Java installation and JVM args that have been specified for your build, if any.
This generally works the same way as if you were using the Gradle wrapper. For example, the profiler will use the values
from your Gradle wrapper properties file, if present, to determine which Gradle version to run.

You can use the `--gradle-version` option to specify a Gradle version or installation to use to benchmark the build. You can specify multiple versions
and each of these is used to benchmark the build, allowing you to compare the behaviour of several different Gradle versions.

## Profiling a build

Profiling allows you to get deeper insight into the performance of your build.

To run the `gradle-profiler` app to profile a build use:

    > gradle-profiler --profile <name-of-profiler> --project-dir <root-dir-of-build> <task>...


The app will run the build several times to warm up a daemon, then enable the profiler and run the build.
Once complete, the results are available under `profile-out`

### Gradle build scans

In order to create a [build scan](https://gradle.com) of your build, use `--profile buildscan`. The build scan URL is available in `profile-out/profile.log`. You can then use the powerful timeline view
in the build scan to analyze which tasks ran, how long they took, how well your build parallelized etc. Also make sure to look at the performance tab to see where time was spent and for hints on how to optimize your build.

### JProfiler

In order to work with JProfiler, use the `--profile jprofiler` option.

This will use JProfiler's CPU sampling by default. JProfiler supports several other options:

- Enable CPU sampling of all methods by adding `--jprofiler-config sampling-all` (by default only packages containing the word `gradle` are sampled)
- Switch to CPU instrumentation by adding `--jprofiler-config instrumentation`
- Enable memory allocation recording by adding `--jprofiler-alloc`
- Enable monitor usage recording by adding `--jprofiler-monitors`
- Enable probes with `--jprofiler-probes:<probe ids, separated by comma>` (e.g. `--jprofiler-probes builtin.FileProbe`)
- Enable heapdump after build with `--jprofiler-heapdump`
- Use a specific profiler session (for full control over filters, sampling intervals etc.) by adding `--jprofiler-session <sessionId>`
- use a different JProfiler installation with `--jprofiler-home /path/to/jprofiler`

### YourKit

In order to work with YourKit, make sure `YOURKIT_HOME` environment variable is set and then use the `--profile yourkit` option.

This will use YourKit's CPU instrumentation by default. You can switch to CPU sampling by adding the `--yourkit-sampling` option. You can switch to memory allocation profiling by adding the `--yourkit-memory` option. All probes are disabled when using sampling or memory allocation profiling.

### Java Flight Recorder

In order to profile with JFR, add the `--profile jfr` option. Note that JFR has a very low sampling frequency compared to other profilers and is unlikely to be helpful for short builds.

### Honest Profiler

Install both [Honest Profiler](https://github.com/RichardWarburton/honest-profiler) and the [FlameGraph](https://github.com/brendangregg/FlameGraph) tool. Then you can run with the options `--profile hp --hp-home /path/to/honest/profiler --fg-home /path/to/flamegraph`

Honest Profiler currently only works on Linux.

### Linux Perf

In order to profile with `perf`, add the `--profile perf` option. `perf` has several advantages over Java agent based profiling:

- Accurate and low overhead
- All Java processes forked by the build are included
- Native stack frames are included
- Inlined methods can be optionally unfolded

#### Prerequisites

  - Linux Kernel 4.7 or later
  - `perf` and `cmake`. For example:

        sudo apt install linux-tools-`uname -r` linux-tools-generic cmake

  - Set the `kernel.perf_event_max_stack` Kernel parameter to accommodate deep Java stacks:

  `sudo sysctl kernel.perf_event_max_stack=1024`

  to make the setting persistent

  ```bash
  echo kernel.perf_event_max_stack=1024 | sudo tee /etc/sysctl.d/99-perf.conf
  ```

  - `sudo` access to `/usr/bin/perf`, `/bin/kill` and `gradle-user-home/tools/brendangregg/Misc/java/jmaps-updated`. 
  
  [An example script exists to configure `sudo` access](src/main/resources/org/gradle/profiler/perf/configure_sudo_access.sh)

  Alternatively add these lines with `sudo visudo` (adapt example location `/home/user/.gradle-profiler` as needed)
```
Defaults!/usr/bin/perf use_pty
%sudo ALL=(ALL) NOPASSWD: /usr/bin/perf, /bin/kill, /home/user/.gradle-profiler/tools/brendangregg/Misc/java/jmaps-updated
```
  The `use_pty` sudo default option is required when `perf` is running with `sudo` in a background process. Without
  `use_pty`, [sudo won't relay the kill signal (SIGQUIT)](https://www.sudo.ws/man/1.8.14/sudo.man.html#Signal_handling)
  to the perf process and it won't be possible to stop profiling cleanly.

  - add `--gradle-user-home $HOME/.gradle-profiler` parameter to the `gradle-profiler` command-line


The profiler automatically downloads and builds the other required tools.

### Chrome Trace

Add the `--profile chrome-trace` option and open the result in Google Chrome. It shows a low-level event dump (e.g. projects being evaluated, tasks being run etc.) together with CPU and memory usage as well as GC activity. Note that using chrome-trace requires Gradle 3.3+.

## Command line options

- `--project-dir`: Directory containing the build to run (required).
- `--benchmark`: Benchmark the build. Runs the builds more times and writes the results to a CSV file.
- `--profile <profiler>`: Profile the build using the specified profiler. See above for details on each profiler.
- `--gradle-version <version>`: Specifies a Gradle version or installation to use to run the builds, overriding the default for the build. You can specify multiple versions.
- `--output-dir <dir>`: Directory to write results to.
- `--no-daemon`: Uses the gradle command-line client with the `--no-daemon` option to run the builds. The default is to use the Gradle tooling API and Gradle daemon.
- `--cli`: Uses the gradle command-line client to run the builds. The default is to use the Gradle tooling API.
- `-D<key>=<value>`: Defines a system property when running the build, overriding the default for the build.
- `--warmups`: Specifies the number of warm-up builds to run for each scenario. Defaults to 2 for profiling, 6 for benchmarking.
- `--iterations`: Specifies the number of builds to run for each scenario. Defaults to 1 for profiling, 10 for benchmarking.
- `--bazel`: Benchmark scenarios using Bazel instead of Gradle. By default, only Gradle scenarios are run. You cannot `--profile` a Bazel build using this tool.
- `--buck`: Benchmark scenarios using Buck instead of Gradle. By default, only Gradle scenarios are run. You cannot `--profile` a Buck build using this tool.
- `--maven`: Benchmark scenarios using Maven instead of Gradle. By default, only Gradle scenarios are run. You cannot `--profile` a Maven build using this tool.

## Advanced profiling scenarios

A scenario file can be provided to define more complex scenarios to benchmark or profile. Use the `--scenario-file` option to provide this. The scenario file is defined in [Typesafe config](https://github.com/typesafehub/config) format.

The scenario file defines one or more scenarios. You can select which scenarios to run by specifying its name on the command-line when running `gradle-profiler`, e.g.

    > gradle-profiler --benchmark --project-dir <root-dir-of-build> --scenario-file performance.scenarios clean_build

Here is an example:

    # Scenarios are run in alphabetical order
    assemble {
        tasks = ["assemble"]
    }
    clean_build {
        versions = ["3.1", "/Users/me/gradle"]
        tasks = ["build"]
        gradle-args = ["--parallel"]
        system-properties {
            key = "value"
        }
        cleanup-tasks = ["clean"]
        run-using = tooling-api // value can be "cli", "no-daemon" or "tooling-api"

        buck {
            targets = ["//thing/res_debug"]
            type = "android_binary" // can be a Buck build rule type or "all"
        }

        warm-ups = 10
    }

Values are optional and default to the values provided on the command-line or defined in the build.

### Profiling incremental builds

A scenario can define changes that should be applied to the source before each build. You can use this to benchmark or profile an incremental build. The following mutations are available:

- `apply-abi-change-to`: Add a public method to a Java or Kotlin source class. Each iteration adds a new method and removes the method added by the previous iteration.
- `apply-non-abi-change-to`: Change the body of a public method in a Java or Kotlin source class.
- `apply-h-change-to`: Add a function to a C/C++ header file. Each iteration adds a new function declaration and removes the function added by the previous iteration. 
- `apply-cpp-change-to`: Add a function to a C/C++ source file. Each iteration adds a new function and removes the function added by the previous iteration. 
- `apply-property-resource-change-to`: Add an entry to a properties file. Each iteration adds a new entry and removes the entry added by the previous iteration.
- `apply-android-resource-change-to`: Add a string resource to an Android resource file. Each iteration adds a new resource and removes the resource added by the previous iteration.
- `apply-android-resource-value-change-to`: Change a string resource in an Android resource file.
- `apply-android-manifest-change-to`: Add a permission to an Android manifest file.
- `clear-build-cache-before`: Deletes the contents of the build cache before the scenario is executed (`SCENARIO`), before cleanup (`CLEANUP`) or before the build is executed (`BUILD`).
- `clear-transform-cache-before`: Deletes the contents of the transform cache before the scenario is executed (`SCENARIO`), before cleanup (`CLEANUP`) or before the build is executed (`BUILD`).
- `show-build-cache-size`: Shows the number of files and their size in the build cache before scenario execution, and after each cleanup and build round..
- `git-checkout`: Checks out a specific for the build step, and a different one for the cleanup step.
- `git-revert`: Reverts a given set of commits before the build and resets it afterward.

They can be added to a scenario file like this:

    incremental_build {
        tasks = ["assemble"]

        apply-abi-change-to = "src/main/java/MyThing.java"
        apply-non-abi-change-to = ["src/main/java/MyThing.java", "src/main/java/MyOtherThing.java"]
        apply-h-change-to = "src/main/headers/app.h"
        apply-cpp-change-to = "src/main/cpp/app.cpp"
        apply-property-resource-change-to = "src/main/resources/thing.properties"
        apply-android-resource-change-to = "src/main/res/value/strings.xml"
        apply-android-resource-value-change-to = "src/main/res/value/strings.xml"
        apply-android-manifest-change-to = "src/main/AndroidManifest.xml"
        clear-build-cache-before = SCENARIO
        clear-transform-cache-before = BUILD
        show-build-cache-size = true
        git-checkout = {
            cleanup = "efb43a1"
            build = "master"
        }
        git-revert = ["efb43a1"]
    }

### Comparing against other build tools

You can compare Gradle against Bazel, Buck, and Maven by specifying their equivalent invocations in the scenario file. Only benchmarking mode is supported.

#### Maven

    > gradle-profiler --benchmark --maven clean_build

    clean_build {
        tasks = ["build"]
        cleanup-tasks = ["clean"]
        maven {
            targets = ["clean build"]
        }
    }

#### Bazel

    > gradle-profiler --benchmark --bazel build_some_target

    build_some_target {
        tasks = ["assemble"]

        bazel {
            targets = ["//some/target"]
        }
    }
    
#### Buck

    > gradle-profiler --benchmark --buck build_binaries

    build_binaries {
        tasks = ["assemble"]

        buck {
            type = "android_binary" // can be a Buck build rule type or "all"
        }
    }
    build_resources {
        tasks = ["thing:processDebugResources"]

        buck {
            targets = ["//thing/res_debug"]
        }
    }
