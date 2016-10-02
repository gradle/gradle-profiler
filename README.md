A tool for gathering profiling and benchmarking information for Gradle builds. Profiling information is captured using the Java flight recorder built into the Oracle JVM.

## Installing

First, build and install the `gradle-profiler` app using:

    > ./gradlew installDist
 
This will install the application into `./build/install/gradle-profiler`.

## Profiling a build

To run the `gradle-profiler` app use:

    > ./build/install/gradle-profiler/bin/gradle-profiler --project-dir <root-dir-of-build> <task>...
    
Where `<root-dir-of-build>` is the directory containing the build to be profiled, and `<task>` is the name of the task to run,
exactly as you would use for the `gradle` command.

The profiler will run the build several times to warm up a daemon, then enable the flight recorder and run the build.
Once complete, the results will be written to a file called `profile.jfr`.

When the profiler runs the build, it will use the tasks you specified. The profiler will use the default
Gradle version, Java installation and JVM args that have been specified for your build, if any.
This generally works the same way as if you were using the Gradle wrapper. For example, the profiler will use the values 
from your Gradle wrapper properties file, if present, to determine which Gradle version to run.

You can use the `--gradle-version` option to specify a Gradle version or installation to use to run the build, overriding the version specified in 
the Gradle wrapper properties file.

## Benchmarking a build

Run the app using:

    > ./build/install/gradle-profiler/bin/gradle-profiler --benchmark --project-dir <root-dir-of-build> <task>...

Results will be written to a file called `benchmark.csv`.

You can use the `--gradle-version` option to specify a Gradle version or installation to use to benchmark the build. You can specify multiple versions
and each of these is used to benchmark the build, allowing you to compare the behaviour of several different Gradle versions.

## Configuration file

A configuration file can be provided to define scenarios to benchmark or profile. Use the `--config-file` option to provide this.

    # A comment
    assemble {
        versions = ["3.0", "3.1"]
        tasks = ["assemble"]
    }
    clean_build {
        versions = ["/Users/me/gradle"]
        tasks = ["clean", "build"]
    }
