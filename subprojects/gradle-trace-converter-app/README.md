# Gradle Build Operation Trace Converter

Command-line tool for converting build operation traces of Gradle into [Perfetto](https://ui.perfetto.dev/) trace format (Protobuf).

## How to install

From the root of the gradle-profiler repository:

1. `./gradlew :gradle-trace-converter-app:install` — installs the distribution to `subprojects/gradle-trace-converter-app/distribution`
2. Add the distribution `bin/` directory to your `$PATH`, or create an alias in your shell startup script (e.g. `.zshrc`, `.bashrc`)

You can choose a different installation location by providing the `gtc.install.dir` property.
E.g. `./gradlew :gradle-trace-converter-app:install -Pgtc.install.dir=/usr/local/bin`.

## Capturing a trace

In order to convert the trace to Perfetto format, you need to capture it.

### Command-line

For **command-line invocations** you can pass the `org.gradle.internal.operations.trace` system property:

```sh
cd /path/to/project

./gradlew -Dorg.gradle.internal.operations.trace.tree=false \
    -Dorg.gradle.internal.operations.trace=/path/to/project/trace

# Writes the trace to '/path/to/project/trace-log.txt'
```

The trace appears in the destination directory, using the last part of the path as the filename prefix,
and appending `-log.txt` at the end.

Setting `org.gradle.internal.operations.trace.tree` to `false` avoids producing extra files, which are not required for trace conversion.

### IDE Sync

For **sync builds** with IntelliJ IDEA or Android Studio, you need to provide the properties via `jvmargs`:

```properties
# gradle.properties

org.gradle.jvmargs= <your properties> \
    -Dorg.gradle.internal.operations.trace.tree=false \
    -Dorg.gradle.internal.operations.trace=/path/to/project/trace

# Writes the trace to '/path/to/project/trace-log.txt'
```

**Warn**: you have to specify absolute paths,
otherwise the files are resolved relative to the working directory of the daemon,
which for sync is not the project directory.

**Note**: any Gradle invocation by the IDE would override the trace file.
Make sure to change the prefix after each trace you capture to avoid this.

## Converting a trace

```sh
gtc /path/to/project/trace-log.txt

# Creates: /path/to/project/trace.perfetto.proto
```

The output file is placed in the same directory as the input, with the `-log.txt` suffix replaced by `.perfetto.proto`.

The trace can be viewed in the [Perfetto UI](https://ui.perfetto.dev/) — drag and drop the file onto the page.

## Requirements

Requires JVM 17 to build and run
