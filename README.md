A tool for gathering profiling information from Gradle builds, using the Java flight recorder built into the Oracle JVM.

To use:

First, build and install the `gradle-profiler` app using:

    > ./gradlew installApp
 
This will install the application into `./build/install/gradle-profiler`.

To run the `gradle-profiler` app use:

    > ./build/install/gradle-profiler/bin/gradle-profiler --project-dir <root-dir-of-build> <task>...
    
Where `<root-dir-of-build>` is the directory containing the build to be profiled, and `<task>` is the name of the task to run.

The profiler will run the build several times to warm up a daemon, then enable profiling and run the build.
The results will be written to a file called `profile.jfr`.
