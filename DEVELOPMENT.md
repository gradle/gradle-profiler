# Development Guide

This guide provides essential information for developing and contributing to Gradle Profiler.

## Prerequisites

* **Java 17**: Required to build and run Gradle Profiler.
* **Java 11**: Required for Gradle cross-version tests.

## Project Structure Overview

The project is organized into a core application and several subprojects handling specific integrations:

* `src`: Contains the core profiling logic, CLI parsing, and reporting mechanisms.
* `subprojects`:
    * `build-operations`, `build-operations-measuring`: Infrastructure for measuring internal build operations.
    * `chrome-trace`, `perfetto-trace`: Generation of performance traces.
    * `heap-dump`: Capturing memory snapshots.
    * `studio-agent`, `studio-plugin`: Integrations for Android Studio sync profiling.
    * `gradle-trace-converter-app`: A standalone utility for converting traces.

## Sanity Check

The project includes a `sanityCheck` task that provides a quick way to validate that the build is still well-defined.
It performs basic checks, such as verifying project descriptions, without running the full test suite.

```bash
./gradlew sanityCheck
```

## Compiling sources

To build the project and compile all classes without running tests:

```bash
./gradlew assemble
```

## Local installation

During development, you can install the `gradle-profiler` app locally to test your changes.

```bash
./gradlew install
```

This installs the executable into `./distribution/gradle-profiler/bin`.
You can then run it from there:

```bash
./distribution/gradle-profiler/bin/gradle-profiler --help
```

*Tip: You can change the installation directory using `-Pgradle-profiler.install.dir=/path/to/dir`.*

## Testing

The project uses [Spock](https://spockframework.org/) (Groovy) for its test suite.
The majority of tests are located in `src/test/groovy`.

The suite is a mix of unit tests, integration tests, and Gradle cross-version tests.
Some tests require external tools like YourKit or JProfiler to be available on the system, and will be skipped otherwise.

### Running Tests

Running `./gradlew test` will run all tests, including integration and cross-version tests.
This process can be very slow and is not advised for local development.
Instead, it is best to run specific tests using Gradle's standard test filtering:

```bash
./gradlew :test --tests 'org.gradle.profiler.CommandLineIntegrationTest.can show version with #option'
```

If you are running cross-version tests during local development, it is highly recommended to set the `testVersions` property.
See the next section for more details.

### Gradle Cross-Version Tests

Gradle cross-version tests verify Gradle Profiler behavior against multiple Gradle versions.

By default, each test will run against a number of Gradle versions:
the latest supported, the earliest, and some versions in between.
This is required to get full test coverage on CI.
However, this can significantly slow down development when iterating locally.

Set the `testVersions` property to `latest` or `partial` to reduce the set of used Gradle versions.

On the command line you can do that with `-PtestVersions=latest`:

```bash
./gradlew -PtestVersions=latest :test --tests '*.GradleInvocationGradleCrossVersionTest.can benchmark using `gradle` command and warm daemon'
```

In the IDE you can either modify the run configurations to pass the same parameter, or you can temporarily modify `gradle.properties`:

```properties
# gradle.properties
testVersions=partial
```

Supported values for the `testVersions` parameter:

- `latest` or `default` -- only the latest among applicable versions
- `partial` -- only the latest and the earliest
- `all` -- all testable versions

The full set of testable versions is specified in `org.gradle.profiler.fixtures.compatibility.gradle.GradleVersionCompatibility#testedGradleVersions`.

Note that individual tests can filter out versions, e.g. when a relevant Gradle feature is not available in older versions.

### Inspecting Test Output

Integration tests and, by extension, cross-version tests forward Gradle Profiler output to the stdout of the test,
so it can be conveniently inspected there.

If you are not running tests from the IDE, which automatically organizes output per test,
avoid running tests with `--info` or similar, since it is likely to produce too much output, making it hard to find the important bits.
Instead, it is recommended to use HTML test reports which are fully local and self-contained.

When tests fail, the link to the test report is generally printed in the Gradle output.
However, the most recent report will always be available at `build/reports/tests/test/index.html`.
You can use the built-in navigation in the page, or if you are interested in the results of a particular test class,
a dedicated page can be found under `classes`,
e.g. `build/reports/tests/test/classes/org.gradle.profiler.ProfilerIntegrationTest.html`.

Because stdout is well integrated into the IDE and HTML reports, it's recommended to print temporary information there,
if it is required during test troubleshooting.

#### Detailed Gradle Profiler output

In more rare cases you might need to look at the detailed output of the Gradle Profiler.
Most tests will write this to the `output/profile.log` file in the test directory.

For each test invocation, a new temporary test directory is allocated under `build/tmp/test-files`.
The test directory naming follows a pattern of `<shortened test name>/<short unique id>`.
E.g. for `GradleInvocationGradleCrossVersionTest`, the short name will be `GradleInvoc.Test`.

A complete location of a profile log file can look like this: `build/tmp/test-files/GradleInvoc.Test/piiqm/output/profile.log`.

In case a test fails, its test directory is preserved, so it can be inspected even after the build has finished.

If a test passes, its test directory is removed.
However, sometimes it's required to inspect the `profile.log` of a successful test.
There are a few options:

1. Run with `-PkeepTestDirs=true`. This will automatically suppress test directory cleanup.
2. Suppress cleanup programmatically via `tmpDir.suppressCleanup()`, e.g. in the `setup()` method.
3. Make the test fail artificially.

If you don't want to change test code, the first option is recommended.

## CI/CD and Compatibility

Our continuous integration (configured via TeamCity in `.teamcity`) ensures compatibility across a matrix of
environments.

When contributing significant changes, consider how they might behave across:

* **Operating Systems**: Linux, macOS, Windows
* **Java Versions**: 17, 21, and 25
