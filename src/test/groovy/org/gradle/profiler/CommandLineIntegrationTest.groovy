package org.gradle.profiler

import org.gradle.profiler.fixtures.AbstractIntegrationTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CommandLineIntegrationTest extends AbstractIntegrationTest {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def "help output"() {
        when:
        new Main().run("--help")
        then:
        output.readLines().collect { it.stripTrailing() }.join("\n") == """Non-option arguments:
[String] -- The scenarios or task names to run

Option                                   Description
------                                   -----------
-D <String>                              Defines a system property
--async-profiler-alloc-interval          The sampling interval in bytes for
  <Integer>                                allocation profiling. Default is 512
                                           KiB. (default: 524287)
--async-profiler-counter                 The counter to use, either 'samples'
  <samples|total>                          or 'totals' (default: SAMPLES)
--async-profiler-event <String>          The event to sample, e.g. 'cpu' or
                                           'alloc'. (default: cpu)
--async-profiler-home <File>             Async Profiler home directory
--async-profiler-interval <Integer>      The sampling interval in nanoseconds.
                                           Default is 10ms. (default: 10000000)
--async-profiler-lock-threshold          lock profiling threshold in
  <Integer>                                nanoseconds. Default is 250 us.
                                           (default: 250000)
--async-profiler-stackdepth <Integer>    The maximum Java stack depth.
                                           (default: 2048)
--async-profiler-system-threads          Whether to show system threads like GC
  <Boolean>                                and JIT compiler. (default: false)
--async-profiler-wall-interval           wall clock profiling interval in
  <Integer>                                nanoseconds. Default is 10ms.
                                           (default: 10000000)
--bazel                                  Benchmark scenarios using Bazel
--benchmark                              Collect benchmark metrics
--buck                                   Benchmark scenarios using buck
--build-ops-trace                        Enable Gradle build operations trace
--buildscan-version [String]             Version of the Build Scan plugin
--cli                                    Uses the command-line client to
                                           connect to the daemon
--cold-daemon                            Use a cold Gradle daemon
--csv-format <String>                    The CSV format produced (LONG, WIDE)
                                           (default: wide)
--dry-run                                Verify configuration
--dump-scenarios                         Dump resolved config for scenario(s)
                                           without running
--gradle-user-home <File>                The Gradle user home to use (default:
                                           gradle-user-home)
--gradle-version <String>                Gradle version or installation to use
                                           to run build
--group <String>                         Run scenarios from a group
-h, --help                               Show this usage information
--iterations <Integer>                   Number of builds to run for each
                                           scenario
--jfr-settings <String>                  JFR settings - Either a .jfc file or
                                           the name of a template known to your
                                           JFR installation
--jprofiler-alloc                        Record allocations
--jprofiler-config [String]              JProfiler built-in configuration name
                                           (sampling|sampling-
                                           all|instrumentation) (default:
                                           sampling)
--jprofiler-config-file <String>         Use another config file for --
                                           jprofiler-session-id instead of the
                                           global config file
--jprofiler-heapdump                     Trigger heap dump after a build
--jprofiler-home <String>                JProfiler installation directory
--jprofiler-monitors                     Record monitor usage
--jprofiler-probes <String>              Record probes (builtin.
                                           FileProbe|builtin.
                                           SocketProbe|builtin.
                                           ProcessProbe|builtin.
                                           ClassLoaderProbe|builtin.
                                           ExceptionProbe, see Controller
                                           javadoc for the full list) separated
                                           by commas, add :+events to probe
                                           name to enable event recording
--jprofiler-session-id <String>          Use session with this id from the
                                           JProfiler installation instead of
                                           using the built-in config
--maven                                  Benchmark scenarios using Maven
--measure-build-op <operation[:metric]>  Build operation type to measure by a
                                           given metric (default:
                                           cumulative_time; options:
                                           cumulative_time,
                                           time_to_first_exclusive,
                                           time_to_last_inclusive,
                                           wall_clock_time)
--measure-config-time                    Include a breakdown of configuration
                                           time in benchmark results
--measure-gc                             Measure the GC time during each
                                           invocation
--measure-local-build-cache              Measure the size of the local build
                                           cache
--no-daemon                              Do not use the Gradle daemon
--no-diffs                               Do not generate differential flame
                                           graphs
--no-studio-sandbox                      Marks that Android Studio should not
                                           use sandbox
--output-dir <File>                      Directory to write results to
                                           (default: new directory with
                                           'profile-out' prefix)
--profile <String>                       Collect profiling information using
                                           profiler (heap-dump, async-profiler-
                                           wall, async-profiler-heap, async-
                                           profiler-all, async-profiler,
                                           buildscan, yourkit, yourkit-tracing,
                                           yourkit-heap, chrome-trace, jfr,
                                           jprofiler) (default: jfr)
--project-dir <File>                     The directory containing the build to
                                           run (default: working directory)
--scenario-file <File>                   Scenario definition file to use
--studio-install-dir <File>              The Studio installation to use
--studio-sandbox-dir <File>              The Studio sandbox dir to use
--title [String]                         Title to show on benchmark report
-v, --version                            Display version information
--warmups <Integer>                      Number of warm-up build to run for
                                           each scenario
"""
    }

    def "can show help with #option"() {
        when:
        new Main().run(option)
        then:
        output =~ /-h, --help\s+Show this usage information/

        where:
        option << ["-h", "--help"]
    }

    def "can show version with #option"() {
        when:
        new Main().run(option)
        then:
        output =~ /Gradle Profiler version .*/

        where:
        option << ["-v", "--version"]
    }

    def "--dump-scenarios requires --scenario-file"() {
        when:
        new Main().run("--benchmark", "--dump-scenarios")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "--dump-scenarios requires a scenario file"
    }

    def "can dump simple scenario"() {
        given:
        def scenarioFile = tmpDir.newFile("test.conf")
        scenarioFile << """
            my-scenario {
                tasks = ["help"]
                warm-ups = 3
            }
        """

        when:
        new Main().run("--benchmark", "--scenario-file", scenarioFile.absolutePath, "--dump-scenarios", "my-scenario")

        then:
        output.trim() == """# Scenario 1/1
my-scenario {
    tasks=[
        help
    ]
    warm-ups=3
}"""
    }
}
