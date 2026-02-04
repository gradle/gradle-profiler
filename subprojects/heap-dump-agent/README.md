# Gradle Lifecycle Agent

A Java agent that creates heap dumps at various points during the Gradle build lifecycle.

## Overview

This agent instruments Gradle's `DefaultBuildLifecycleController` class to intercept key lifecycle methods and automatically create heap dumps at configurable points during a build. The agent supports two interception strategies:

- **config-end**: Captures heap dumps when the configuration phase ends and execution phase begins (at `finalizeWorkGraph()`)
- **build-end**: Captures heap dumps when the build completes after all tasks execute (at `finishBuild()`)

## Use Cases

- Analyzing memory usage at different stages of the Gradle build lifecycle
- Debugging memory leaks and object retention issues
- Comparing memory state between configuration and execution phases
- Understanding what objects are alive at build completion
- Profiling Gradle builds to optimize both configuration and execution performance

## Building

Build the agent JAR:

```bash
./gradlew :heap-dump-agent:build
```

The agent JAR will be created at:
```
subprojects/heap-dump-agent/build/libs/heap-dump-agent-<version>.jar
```

## Usage

### Using with Gradle Profiler (Recommended)

The easiest way to use this agent is through the gradle-profiler tool:

```bash
gradle-profiler --profile heap-dump --heap-dump-when build-end assemble
gradle-profiler --profile heap-dump --heap-dump-when config-end assemble
gradle-profiler --profile heap-dump --heap-dump-when config-end,build-end assemble
```

See the main gradle-profiler README for more details.

### Direct Usage

Add the agent to your Gradle build using the `-javaagent` JVM argument:

```bash
./gradlew build -Dorg.gradle.jvmargs="-javaagent:/path/to/heap-dump-agent-<version>.jar=/output/dir;build-end"
```

#### Agent Argument Format

The agent accepts arguments in the format: `<outputDir>;<strategy1>,<strategy2>`

Examples:
- `/path/to/output;build-end` - Single strategy (default)
- `/path/to/output;config-end` - Config-end only
- `/path/to/output;config-end,build-end` - Both strategies

If no strategy is specified, `build-end` is used by default.

### Using with gradle.properties

For persistent configuration, add to your `gradle.properties`:

```properties
org.gradle.jvmargs=-javaagent:/path/to/heap-dump-agent-<version>.jar=/path/to/output;config-end,build-end
```

### Using with GRADLE_OPTS

Set the `GRADLE_OPTS` environment variable:

```bash
export GRADLE_OPTS="-javaagent:/path/to/heap-dump-agent-<version>.jar=/path/to/output;build-end"
./gradlew build
```

## Output

The agent creates timestamped HPROF files with prefixes indicating when they were captured:

- `gradle-config-end-20260204-184500.hprof` - Captured at configuration end
- `gradle-build-end-20260204-184600.hprof` - Captured at build completion

When the agent runs, you'll see output like:

```
!!! Gradle Lifecycle Agent Started
!!! Heap dump output directory: /path/to/output
!!! Active strategies: [config-end, build-end]
!!! Added agent JAR to bootstrap classloader: /path/to/heap-dump-agent.jar
!!! Instrumenting DefaultBuildLifecycleController
!!! Instrumenting finalizeWorkGraph for config-end
!!! Instrumenting finishBuild for build-end

--------------------------------------------------------------------------------
Configuration Stage Ending - Creating Heap Dump
--------------------------------------------------------------------------------
Heap dump location: /path/to/output/gradle-config-end-20260204-184500.hprof
Creating heap dump...
Heap dump created successfully in 1234ms
File: /path/to/output/gradle-config-end-20260204-184500.hprof
--------------------------------------------------------------------------------

... (tasks execute) ...

--------------------------------------------------------------------------------
Build Finishing - Creating Heap Dump
--------------------------------------------------------------------------------
Heap dump location: /path/to/output/gradle-build-end-20260204-184600.hprof
Creating heap dump...
Heap dump created successfully in 1456ms
File: /path/to/output/gradle-build-end-20260204-184600.hprof
--------------------------------------------------------------------------------
```

## Analyzing the Heap Dump

The generated `.hprof` files can be analyzed with various tools:

- **Eclipse MAT (Memory Analyzer Tool)**: https://www.eclipse.org/mat/
- **VisualVM**: https://visualvm.github.io/
- **IntelliJ IDEA**: Built-in heap dump analyzer
- **JProfiler**: Commercial profiler with heap dump analysis
- **YourKit**: Commercial profiler with heap dump analysis

## Implementation Details

The agent uses:
- **ASM** (ObjectWeb ASM 9.2) for bytecode manipulation and instrumentation
- **Java Instrumentation API** to transform classes at load time and manage classloader visibility
- **Bootstrap Classloader Search Path** to make interceptor classes available to all classloaders
- **HotSpotDiagnosticMXBean** to generate heap dumps programmatically
- Only live objects are included in dumps (garbage collected objects are excluded)

### Architecture

The agent uses a clean, extensible architecture based on composition and interfaces:

#### 1. Strategy Pattern (`strategy/` package)

- **`HeapDumpStrategy` interface**: Defines the contract for interception strategies
  - `getOptionValue()`: Returns CLI option name (e.g., "config-end", "build-end")
  - `getFilePrefix()`: Returns heap dump filename prefix (e.g., "gradle-config-end")
  - `getInterceptionMessage()`: Returns console message displayed when intercepting
  - `getTargetMethodName()`: Returns method name to intercept
  - `getTargetMethodDescriptor()`: Returns method descriptor for bytecode instrumentation

- **`ConfigEndStrategy`**: Implements `HeapDumpStrategy` for configuration phase end
  - Intercepts `finalizeWorkGraph()` in `DefaultBuildLifecycleController`
  - Static entry point: `onFinalizeWorkGraph(String outputPath)`

- **`BuildEndStrategy`**: Implements `HeapDumpStrategy` for build completion
  - Intercepts `finishBuild()` in `DefaultBuildLifecycleController`
  - Static entry point: `onFinishBuild(String outputPath)`

**Adding new strategies**: Simply create a class implementing `HeapDumpStrategy`, add it to `AVAILABLE_STRATEGIES` in `GradleLifecycleAgent`, and add corresponding instanceof checks for bytecode generation.

#### 2. Agent Entry Point (`GradleLifecycleAgent.premain`)

- Parses agent arguments: `<outputDir>;<strategy1>,<strategy2>`
- Looks up strategies by option value from `AVAILABLE_STRATEGIES` list
- Defaults to `build-end` if no strategies specified
- Registers the agent JAR with bootstrap classloader search path
- Creates and registers a `LifecycleInstrumentingTransformer`

#### 3. Bytecode Transformation (`LifecycleInstrumentingTransformer`)

- Monitors for `org.gradle.internal.build.DefaultBuildLifecycleController` being loaded
- For each active strategy:
  - Checks if method name and descriptor match the strategy's target
  - Uses ASM to inject `INVOKESTATIC` call to strategy's static entry point at method start
  - Passes output path as argument

#### 4. Heap Dump Creation (`HeapDumpInterceptor`)

- Uses **composition** with `HeapDumpStrategy` to determine behavior
- Thread-safe singleton pattern: volatile boolean + synchronized block
- Creates heap dumps using `HotSpotDiagnosticMXBean.dumpHeap()`
- Only live objects included in dumps
- Filename format: `<strategy-prefix>-<timestamp>.hprof`
- One dump per build per strategy, even with multiple method invocations

## Requirements

- Java 11 or later
- Gradle build using `DefaultBuildLifecycleController` (standard Gradle builds)
- HotSpot JVM (for heap dump generation)

## Troubleshooting

### Agent not instrumenting

Ensure the agent is loaded before Gradle starts. Check for the startup message:
```
!!! Gradle Lifecycle Agent Started
```

### Heap dump not created

- Verify write permissions in the output directory
- Check that the JVM supports heap dumps (HotSpot JVM required)
- Ensure sufficient disk space for the heap dump
- Verify the strategy is spelled correctly in agent arguments

### Multiple heap dumps

The agent ensures only one heap dump per build per strategy is created, even if the intercepted method is called multiple times (e.g., in composite builds or multi-project builds).

### Invalid strategy error

If you see:
```
ERROR: Unknown interception strategy: invalid-value
```

Ensure you're using valid strategy names: `config-end` or `build-end` (comma-separated for multiple).

## License

See the main gradle-profiler LICENSE file.
