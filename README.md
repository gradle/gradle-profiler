# Gradle Profiler

A tool to automate the gathering of profiling and benchmarking information for Gradle builds.

Profiling information can be captured using several different tools:

- Using [Gradle build scans](https://gradle.com)
- Using [Async Profiler](https://github.com/jvm-profiling-tools/async-profiler)
- Using [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html)
- Using [YourKit](https://www.yourkit.com) profiler
- Using [Java flight recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm#JFRUH170)
- Producing a heap dump in HPROF format
- Producing [Chrome Trace](https://www.chromium.org/developers/how-tos/trace-event-profiling-tool) output

## Installing

### SDKMAN!

[SDKMAN!](http://sdkman.io/) is a tool for managing parallel versions of multiple Software Development Kits on most Unix-based systems.

    > sdk install gradleprofiler
    > gradle-profiler --benchmark help

### Homebrew

[Homebrew](https://brew.sh/) is the easiest and most flexible way to install the UNIX tools Apple didn’t include with macOS.

    > brew install gradle-profiler
    > gradle-profiler --benchmark help


### Download binaries

Binaries are available and linked from the [releases](https://github.com/gradle/gradle-profiler/releases) page.

### Build from source

First, build and install the `gradle-profiler` app using:

    > ./gradlew installDist

This will install the executable into `./build/install/gradle-profiler/bin`. The examples below assume that you add this location to your PATH or create a `gradle-profiler` alias for it.

NOTE: You have to use Java 11 or later to build this project.

## Benchmarking a build

Benchmarking simply records the time it takes to execute your build several times and calculates a mean and standard error for it.
It has zero impact on the execution time, so it is ideal for making before/after comparisons for new Gradle versions or changes to your build.

Run the `gradle-profiler` app using:

    > gradle-profiler --benchmark --project-dir <root-dir-of-build> <task>...

Where `<root-dir-of-build>` is the directory containing the build to be benchmarked, and `<task>` is the name of the task to run,
exactly as you would use for the `gradle` command.

Results will be written to a file called `profile-out/benchmark.html` and `profile-out/benchmark.csv`.

When the profiler runs the build, it will use the tasks you specified. The profiler will use the default
Gradle version, Java installation and JVM args that have been specified for your build, if any.
This generally works the same way as if you were using the Gradle wrapper. For example, the profiler will use the values
from your Gradle wrapper properties file, if present, to determine which Gradle version to run.

You can use the `--gradle-version` option to specify a Gradle version or installation to use to benchmark the build.
You can specify multiple versions and each of these is used to benchmark the build, allowing you to compare the behaviour of several different Gradle versions.

You can also use the `--measure-config-time` option to measure some additional details about configuration time.

You can use `--measure-build-op` together with the fully qualified class name of the enveloping type of the `Details` interface to benchmark cumulative build operation time.
For example, for Gradle 5.x there is a [`org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType`](https://github.com/gradle/gradle/blob/c671360a3f1729b406c5b8b5b0d22c7b81296993/subprojects/core/src/main/java/org/gradle/api/internal/tasks/SnapshotTaskInputsBuildOperationType.java) which can be used to capture snapshotting time.
The time recorded is cumulative time, so the wall clock time spent on executing the measured build operations is probably smaller.
If the build operation does not exists in a benchmarked version of Gradle, it is gracefully ignored.
In the resulting reports it will show up with 0 time.

### Regression detection

If multiple versions are tested, then Gradle profiler determines whether there is an statistically significant difference in the run times by using a [Mann-Whitney U-Test](https://en.wikipedia.org/wiki/Mann%E2%80%93Whitney_U_test).
The result files contain the confidence if a sample has a different performance behavior - i.e. it is faster or slower - than the baseline.

## Profiling a build

Profiling allows you to get deeper insight into the performance of your build.

To run the `gradle-profiler` app to profile a build use:

    > gradle-profiler --profile <name-of-profiler> --project-dir <root-dir-of-build> <task>...

The app will run the build several times to warm up a daemon, then enable the profiler and run the build.
Once complete, the results are available under `profile-out/`.

If you use Async profiler or JFR for profiling, Gradle profiler will also create flame graphs for each scenario.
If you profile multiple scenarios or multiple versions, then Gradle profiler will create differential flame graphs as well.

### Gradle build scans

[Gradle build scans](https://gradle.com) are a powerful tool to investigate the structure of your build and quickly find bottlenecks. 
You can use the timeline view to see which tasks ran, how long they took, whether they were cached, how well your build parallelized etc. 
The performance tab will show you details about configuration time and other hints on how to make your build faster.

In order to create a build scan of your build, use `--profile buildscan`. The build scan URL is reported on the console and is also available in `profile-out/profile.log`. 

### Async Profiler

Async profiler provides low-overhead CPU, allocation and perf event sampling on Linux and MacOS. 
It also correctly handles native method calls, making it preferable to JFR on these operating systems. 

You can use async profiler to profile a Gradle build using `--profile async-profiler`. By default, this will profile CPU usage, with some reasonable default settings. These settings can be configured using various command-line options, listed below.

Alternatively, you can also use `--profile async-profiler-heap` to profile heap allocations, with some reasonable default settings.

Finally, you can also use `--profile async-profiler-all` to profile cpu, heap allocations, and locks with some reasonable default settings.

By default, an Async profiler release will be downloaded from [Github](https://github.com/jvm-profiling-tools/async-profiler/releases) and installed, if not already available.

The output are flame and icicle graphs which show you the call tree and hotspots of your code.

The following options are supported and closely mimic the options of Async profiler. Have a look at its readme to find out more about each option:

- `--async-profiler-event`: The event to sample, e.g. `cpu`, `wall`, `lock` or `alloc`. Defaults to `cpu`. Multiple events can be profiled by using this parameter multiple times.
- `--async-profiler-count`: The count to use when aggregating event data. Either `samples` or `total`. `total` is especially useful for allocation profiling. Defaults to `samples`. Corresponds to the `--samples` and `--total` command line options for Async profiler.
- `--async-profiler-interval`: The sampling interval in ns, defaults to 10_000_000 (10 ms).
- `--async-profiler-alloc-interval`: The sampling interval in bytes for allocation profiling, defaults to 10 bytes. Corresponds to the `--alloc` command line option for Async profiler.
- `--async-profiler-lock-threshold`: lock profiling threshold in nanoseconds, defaults to 250 microseconds. Corresponds to the `--lock` command line option for Async profiler.
- `--async-profiler-stackdepth`: The maximum stack depth. Lower this if profiles with deep recursion get too large. Defaults to 2048.
- `--async-profiler-system-threads`: Whether to show system threads like GC and JIT compilation in the profile. Usually makes them harder to read, but can be useful if you suspect problems in that area. Defaults to `false`. 

You can also use either the `ASYNC_PROFILER_HOME` environment variable or the `--async-profiler-home` command line option to point to the Async profiler install directory.

### JProfiler

JProfiler is a powerful commercial profiler, which provides both sampling and instrumentation capabilities.
You can tailor its settings in the JProfiler UI and then instruct the Gradle profiler to use these settings for full control
over what you want to investigate. For instance, you could split calls to a dependency resolution rule by argument to
find out if the rule is slow for a specific dependency.

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

YourKit is a powerful commercial profiler, which provides both sampling and instrumentation capabilities.
Its integration in the Gradle profiler is currently limited, e.g. support for probes and other custom settings
is missing. If you are using YourKit and would like to see better support, pull requests are welcome.

In order to work with YourKit, make sure the `YOURKIT_HOME` environment variable is set and then use the `--profile yourkit` option. This will use YourKit's CPU sampling instrumentation by default. 

You can switch to CPU tracing using the `--profile yourkit-tracing` option. 
You can switch to memory allocation profiling by using the `--profile yourkit-heap` option. 
All probes are disabled when using sampling or memory allocation profiling.

### Java Flight Recorder

JFR provides low overhead CPU, allocation, IO wait and lock profiling and runs on all major operating systems.
It is available on Oracle JDK since Java 7 and on OpenJDK since Java 11 (make sure you have at least [11.0.3](https://bugs.openjdk.java.net/browse/JDK-8219347)).

To our knowledge, it is the only low-overhead allocation profiler for Windows.
However, be aware of its shortcomings, e.g. it will not sample native method calls, so you will get misleading CPU results if your code does a lot of system calls (like reading files). 

You will get both the JFR file and flame graph visualizations of the data, which are much easier to understand than the Java Mission Control UI. 

In order to profile with JFR, add the `--profile jfr` option. 
You can change the profiler settings using `--jfr-settings`, specifying either the path to a `.jfc` file or the name of a built-in template like `profile`.

### Heap dump

To capture a heap dump at the end of each measured build, add the `--profile heap-dump` option. You can use this with other `--profile` options.

### Chrome Trace

Chrome traces are a low-level event dump (e.g. projects being evaluated, tasks being run etc.).
They are useful when you can't create a build scan, but need to look at the overall structure of a build.
It also displays CPU load, memory usage and GC activity. Using chrome-trace requires Gradle 3.3+.

Add the `--profile chrome-trace` option and open the result in Google Chrome in chrome://tracing. 

## Command line options

- `--project-dir`: Directory containing the build to run (required).
- `--benchmark`: Benchmark the build. Runs the builds more times and writes the results to a CSV file.
- `--profile <profiler>`: Profile the build using the specified profiler. See above for details on each profiler.
- `--output-dir <dir>`: Directory to write results to. Default value is `profile-out`. If profile-out directory already exists, it tries to find a `profile-out-<index>` directory, that does not exist.
- `--warmups`: Specifies the number of warm-up builds to run for each scenario. Defaults to 2 for profiling, 6 for benchmarking, and 1 when not using a warm daemon.
- `--iterations`: Specifies the number of builds to run for each scenario. Defaults to 1 for profiling, 10 for benchmarking.
- `--bazel`: Benchmark scenarios using Bazel instead of Gradle. By default, only Gradle scenarios are run. You cannot profile a Bazel build using this tool.
- `--buck`: Benchmark scenarios using Buck instead of Gradle. By default, only Gradle scenarios are run. You cannot profile a Buck build using this tool.
- `--maven`: Benchmark scenarios using Maven instead of Gradle. By default, only Gradle scenarios are run. You cannot profile a Maven build using this tool.

The following command line options only apply when measuring Gradle builds:

- `--gradle-user-home`: The Gradle user home. Defaults to `<project-dir>/gradle-user-home` to isolate performance tests from your other builds.
- `--gradle-version <version>`: Specifies a Gradle version or installation to use to run the builds, overriding the default for the build. You can specify multiple versions by using this option once for each version.
- `--no-daemon`: Uses the `gradle` command-line client with the `--no-daemon` option to run the builds. The default is to use the Gradle tooling API and Gradle daemon.
- `--cold-daemon`: Use a cold daemon (one that has just started) rather than a warm daemon (one that has already run some builds). The default is to use a warm daemon.
- `--cli`: Uses the `gradle` command-line client to run the builds. The default is to use the Gradle tooling API and Gradle daemon.
- `--measure-build-op`: Additionally measure the cumulative time spent in the given build operation. Only supported for Gradle 6.1 and later.
- `--measure-config-time`: Measure some additional details about configuration time. Only supported for Gradle 6.1 and later.
- `--measure-gc`: Measure the garbage collection time. Only supported for Gradle 6.1 and later.
- `--measure-local-build-cache`: Measure the size of the local build cache.
- `-D<key>=<value>`: Defines a system property when running the build, overriding the default for the build.
- `--studio-install-dir`: The Android Studio installation directory. Required when measuring Android Studio sync. On macOS, it is the app directory, e.g. `~/Applications/Android Studio.app`.
- `--studio-sandbox-dir`: The Android Studio sandbox directory. It's recommended to use it since it isolates the Android Studio process from your other Android Studio processes. By default, this will be set to `<output-dir>/studio-sandbox`. If you want Android Studio to keep old data (e.g. indexes) you should set and reuse your own folder. 
- `--no-studio-sandbox`: Do not use the Android Studio sandbox but use the default Android Studio folders for the Android Studio data.
- `--no-diffs`: Do not generate differential flame graphs.

## Advanced profiling scenarios

A scenario file can be provided to define more complex scenarios to benchmark or profile. Use the `--scenario-file` option to provide this. The scenario file is defined in [Typesafe config](https://github.com/typesafehub/config) format.

The scenario file defines one or more scenarios. You can select which scenarios to run by specifying its name on the command-line when running `gradle-profiler`, e.g.

    > gradle-profiler --benchmark --scenario-file performance.scenarios clean_build

Here is an example:

    # Can specify scenarios to use when none are specified on the command line
    default-scenarios = ["assemble"]
    
    # Scenarios are run in alphabetical order
    assemble {
        # Show a slightly more human-readable title in reports
        title = "Assemble"
        # Run the 'assemble' task
        tasks = ["assemble"]
    }
    clean_build {
        title = "Clean Build"
        versions = ["3.1", "/Users/me/gradle"]
        tasks = ["build"]
        gradle-args = ["--parallel"]
        system-properties {
            "key" = "value"
        }
        cleanup-tasks = ["clean"]
        run-using = tooling-api // value can be "cli" or "tooling-api"
        daemon = warm // value can be "warm", "cold", or "none"
        measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"] // see --measure-build-op

        buck {
            targets = ["//thing/res_debug"]
            type = "android_binary" // can be a Buck build rule type or "all"
        }

        warm-ups = 10
    }
    ideaModel {
        title = "IDEA model"
        # Fetch the IDEA tooling model
        tooling-api {
            model = "org.gradle.tooling.model.idea.IdeaProject"
        }
        # Can also run tasks
        # tasks = ["assemble"]
    }
    toolingAction {
        title = "IDEA model"
        # Fetch the IDEA tooling model
        tooling-api {
            action = "org.gradle.profiler.toolingapi.FetchProjectPublications"
        }
        # Can also run tasks
        # tasks = ["assemble"]
    }
    androidStudioSync {
        title = "Android Studio Sync"
        # Measure an Android studio sync
        # Note: Android Studio Hedgehog (2023.1.1) or newer is required
        # Note2: you need to have local.properties file in your project with sdk.dir set
        android-studio-sync {
            # Override default Android Studio jvm args
            # studio-jvm-args = ["-Xms256m", "-Xmx4096m"]
            # Pass an IDEA properties to Android Studio. This can be used to set a registry values as well
            # idea-properties = ["gradle.tooling.models.parallel.fetch=true"]
        }
    }

Values are optional and default to the values provided on the command-line or defined in the build.

### Benchmark options

- `iterations`: Number of builds to actually measure
- `warm-ups`: Number of warmups to perform before measurement
- `jvm-args`: Sets or overrides the jvm arguments set by `org.gradle.jvmargs` in gradle.properties.

### Profiling change handling

How a build tool handles changes to the source code can have a significant impact on the performance of the build.
Gradle Profiler can simulate different kinds of changes to the source code to measure the impact of these changes on the build performance.
These changes are applied by mutators at different points in the build benchmark process.
Some mutators execute at a specific point, others can be configured to execute at a specific point, specified by the `schedule` parameter:

- `SCENARIO`: before the scenario is executed,
- `CLEANUP`: before cleaning preceeding each build invocation,
- `BUILD`: before the build invocation (after cleanup).

#### Source code mutators

These mutations are applied before each build, and they introduce different kinds of change to the source code.

- `apply-abi-change-to`: Add a public method to a Java or Kotlin source class. Each iteration adds a new method and removes the method added by the previous iteration.
- `apply-android-layout-change-to`: Add a hidden view with id to an Android layout file. Supports traditional layouts as well as Databinding layouts with a ViewGroup as the root element.
- `apply-android-manifest-change-to`: Add a permission to an Android manifest file.
- `apply-android-resource-change-to`: Add a string resource to an Android resource file. Each iteration adds a new resource and removes the resource added by the previous iteration.
- `apply-android-resource-value-change-to`: Change a string resource in an Android resource file.
- `apply-build-script-change-to`: Add a statement to a Groovy or Kotlin DSL build script, init script or settings script. Each iteration adds a new statement and removes the statement added by the previous iteration.
- `apply-cpp-change-to`: Add a function to a C/C++ source file. Each iteration adds a new function and removes the function added by the previous iteration. 
- `apply-h-change-to`: Add a function to a C/C++ header file. Each iteration adds a new function declaration and removes the function added by the previous iteration. 
- `apply-kotlin-composable-change-to`: Add a `@Composable` function to a Kotlin source file.
- `apply-non-abi-change-to`: Change the body of a public method in a Java or Kotlin source class.
- `apply-project-dependency-change-to`: Add project dependencies to a Groovy or a Kotlin DSL build script. Each iteration adds a new combination of projects as dependencies and removes the projects added by the previous iteration.
- `apply-property-resource-change-to`: Add an entry to a properties file. Each iteration adds a new entry and removes the entry added by the previous iteration.

#### Cache cleanup

When simulating scenarios like ephemeral builds, it is important to make sure caches are not present.
These mutators can be scheduled to execute at different points in the build benchmark process, specified by the `schedule` parameter.

- `clear-android-studio-cache-before`: Invalidates the Android Studio caches. Due to Android Studio client specifics scheduling to run before cleanup (`CLEANUP`) is not supported. Note: cleaning the Android Studio caches is run only when Android Studio sync (`android-studio-sync`) is used.
- `clear-build-cache-before`: Deletes the contents of the build cache at the given schedule.
- `clear-configuration-cache-state-before`: Deletes the contents of the `.gradle/configuration-cache-state` directory.
- `clear-gradle-user-home-before`: Deletes the contents of the Gradle user home directory.
  The mutator retains the `wrapper` cache in the Gradle user home, since the downloaded wrapper in that location is used to run Gradle.
  Requires to use the `none` daemon option to use with `CLEANUP` or `BUILD` schedules.
- `clear-jars-cache-before`: Deletes the contents of the instrumented jars cache.
- `clear-project-cache-before`: Deletes the contents of the `.gradle` and `buildSrc/.gradle` project cache directories.
- `clear-transform-cache-before`: Deletes the contents of the transform cache.
- `show-build-cache-size`: Shows the number of files and their size in the build cache before scenario execution, and after each cleanup and build round.

#### File operations

- `copy-file`: Copies a file or a directory from one location to another. Has to specify a `source` and a `target` path; relative paths are resolved against the project directory. Can take an array of operations. Defaults to `SCNEARIO` schedule.
- `delete-file`: Deletes a file or a directory. Has to specify a `target` path; when relative it is resolved against the project directory. Can take an array of operations. Defaults to `SCNEARIO` schedule.
- `git-checkout`: Checks out a specific commit for the build step, and a different one for the cleanup step.
- `git-revert`: Reverts a given set of commits before the build and resets it afterward. 

They can be added to a scenario file like this:

    incremental_build {
        tasks = ["assemble"]

        apply-build-script-change-to = "build.gradle.kts"
        apply-project-dependency-change-to {
            files = ["build.gradle"]
            # Default number of dependency-count is 3.
            # Gradle Profiler will simulate changes to project dependencies by generate some additional projects and then add a combination of project dependencies to every non-generated subprojects before each iteration.
            # The profiler will generate the minimal number of subprojects to allow for a unique combination of dependencies to be used for each iteration.
            # Note: Number of generated projects is calculated as binomial coffiecient: "from `x` choose `dependency-count` = `iterations * files`", where number of generated projects is `x`.
            dependency-count = 3
        }
        apply-abi-change-to = "src/main/java/MyThing.java"
        apply-non-abi-change-to = ["src/main/java/MyThing.java", "src/main/java/MyOtherThing.java"]
        apply-h-change-to = "src/main/headers/app.h"
        apply-cpp-change-to = "src/main/cpp/app.cpp"
        apply-property-resource-change-to = "src/main/resources/thing.properties"
        apply-android-resource-change-to = "src/main/res/values/strings.xml"
        apply-android-resource-value-change-to = "src/main/res/values/strings.xml"
        apply-android-manifest-change-to = "src/main/AndroidManifest.xml"
        clear-build-cache-before = SCENARIO
        clear-transform-cache-before = BUILD
        show-build-cache-size = true
        copy-file = {
            source = "../develocity.xml"
            target = ".mvn/develocity.xml"
        }
        delete-file = [{
            target = ".mvn/develocity.xml"
            schedule = CLEANUP
        }, {
            target = ".gradle"
            schedule = CLEANUP
        }]
        git-checkout = {
            cleanup = "efb43a1"
            build = "master"
        }
        git-revert = ["efb43a1"]
        jvm-args = ["-Xmx2500m", "-XX:MaxMetaspaceSize=512m"]
    }

### Comparing against other build tools

You can compare Gradle against Bazel, Buck, and Maven by specifying their equivalent invocations in the scenario file. Only benchmarking mode is supported.

#### Maven

    > gradle-profiler --benchmark --maven clean_build

    clean_build {
        tasks = ["build"]
        cleanup-tasks = ["clean"]
        maven {
            # If empty, it will be infered from MAVEN_HOME environment variable
            home = "/path/to/maven/home"
            targets = ["clean", "build"]
        }
    }

#### Bazel

    > gradle-profiler --benchmark --bazel build_some_target

    build_some_target {
        tasks = ["assemble"]

        bazel {
            # If empty, it will be infered from BAZEL_HOME environment variable
            home = "/path/to/bazel/home"
            targets = ["build" "//some/target"]
        }
    }
    
#### Buck

    > gradle-profiler --benchmark --buck build_binaries

    build_binaries {
        tasks = ["assemble"]

        buck {
            # If empty, it will be infered from BUCK_HOME environment variable
            home = "/path/to/buck/home"
            type = "android_binary" // can be a Buck build rule type or "all"
        }
    }
    build_resources {
        tasks = ["thing:processDebugResources"]

        buck {
            targets = ["//thing/res_debug"]
        }
    }
