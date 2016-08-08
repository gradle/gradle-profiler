A tool for gathering profiling information from Gradle builds, using the Java flight recorder built into the Oracle JVM.

## Installing

First, build and install the `gradle-profiler` app using:

    > ./gradlew installDist
 
This will install the application into `./build/install/gradle-profiler`.

## Running the profiler

To run the `gradle-profiler` app use:

    > ./build/install/gradle-profiler/bin/gradle-profiler --project-dir <root-dir-of-build> <task>...
    
Where `<root-dir-of-build>` is the directory containing the build to be profiled, and `<task>` is the name of the task to run,
exactly as you would use for the `gradle` command.

The profiler will run the build several times to warm up a daemon, then enable profiling and run the build.
Once complete, the results will be written to a file called `profile.jfr`.

When the profiler runs the build, it will use the tasks you specified. The profiler will use the default
Gradle version, Java installation and JVM args that have been specified for your build, if any.
This generally works the same way as if you were using the Gradle wrapper. For example, the profiler will use the values 
from your Gradle wrapper properties file, if present, to determine which Gradle version to run.
