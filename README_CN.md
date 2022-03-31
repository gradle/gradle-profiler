# Gradle Profiler

用于自动收集Gradle构建的分析和基准测试信息的工具。

可以使用几种不同的工具捕获分析信息：

- 使用 [Gradle build scans](https://gradle.com)
- 使用 [Async Profiler](https://github.com/jvm-profiling-tools/async-profiler)
- 使用 [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html)
- 使用 [YourKit](https://www.yourkit.com) profiler
- 使用 [Java flight recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm#JFRUH170)
- 以 HPROF 格式生成堆转储
- 生成 [Chrome Trace](https://www.chromium.org/developers/how-tos/trace-event-profiling-tool) 输出文件

## 安装

### SDKMAN!

[SDKMAN!](http://sdkman.io/) 是一个用于管理基于 Unix 的多并行版本 SDK 的工具。

    > sdk install gradleprofiler
    > gradle-profiler --benchmark help

### Homebrew

[Homebrew](https://brew.sh/) 是安装苹果 macOS 中没有的 UNIX 工具的最简单、最灵活的方式。

    > brew install gradle-profiler
    > gradle-profiler --benchmark help


### 下载二进制

二进制文件可从 [releases](https://github.com/gradle/gradle-profiler/releases) 页获得。

### 源码构建

首先, 通过以下方式构建和安装 `gradle-profiler` 应用：

    > ./gradlew installDist

这将把可执行文件安装到 `./build/install/gradle-profiler/bin` 中。下面的示例假定您将此位置添加到 PATH 或为其创建 `gradle-profiler` 的别名。

注意：您必须使用 Java11 或更高版本来构建此项目。

## 建立基准测试

基准测试简单地记录执行几次构建所需的时间，并为其计算平均值和标准误差。它对执行时间没有任何影响，因此非常适合在新 Gradle 版本或构建更改之前/之后进行比较。

通过以下方式使用 `gradle-profiler` 应用：

    > gradle-profiler --benchmark --project-dir <root-dir-of-build> <task>...

其中 `<root-dir-of-build>` 是包含要基准测试的构建的目录， `<task>` 是需要执行的 task 的名称，
与 `gradle` 命令完全相同。

结果将会被写在 `profile-out/benchmark.html` 文件和 `profile-out/benchmark.csv` 文件中。

profiler 将会以您指定的 task 进行构建。profiler 将默认使用为您指定的默认 Gradle 版本、安装的 Java 和 JVM 参数（如果有的话）进行构建。这通常与您使用 Gradle wrapper 的工作方式相同。例如，profiler 将使用您的Gradle wrapper 属性文件中的值（如果存在）来确定运行哪个 Gradle 版本。

您可以使用 `--gradle-version` 选项指定一个 Gradle 版本或者安装该版本进行构建。您可以指定多个版本，每个版本都会用于基准构建，允许您比较几个不同 Gradle 版本的行为。

您还可以使用 `--measure-config-time` 选项去度量一些关于配置时间的其他详细信息。

您可以使用 `——measure-build-op` 和 `Details` 接口的封装类型的完全限定类名来对累积构建操作时间进行基准测试。举个例子，在 Gradle 5.x 中有一个 [`org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType`](https://github.com/gradle/gradle/blob/c671360a3f1729b406c5b8b5b0d22c7b81296993/subprojects/core/src/main/java/org/gradle/api/internal/tasks/SnapshotTaskInputsBuildOperationType.java) 可以用来捕捉快照时间。所记录的时间是累积时间，因此用于执行所测量的构建操作的时间可能更小。如果构建操作在基准版本的 Gradle 中不存在，它将被优雅地忽略。在结果报告中，它的时间将会显示为0。

### 回归检测

如果测试了多个版本，那么 Gradle profiler 通过使用 [Mann-Whitney U-Test](https://en.wikipedia.org/wiki/Mann%E2%80%93Whitney_U_test) 确定运行时间是否存在显著差异。结果文件包含了一个示例是否具有与基线不同的性能行为（即更快或更慢）的置信度。

## 分析一个构建

分析允许您更深入地了解构建的性能。

通过以下方式使用 `gradle-profiler` 应用：

    > gradle-profiler --profile <name-of-profiler> --project-dir <root-dir-of-build> <task>...

应用将多次运行构建以预热 daemon 进程，然后启用 profiler 并运行构建。完成后，结果可在配置文件 `profile-out/` 下获得。

如果您使用 Async profiler 或 JFR 进行分析，Gradle profiler 还将为每个场景创建火焰图。如果您分析了多个场景或多个版本，那么 Gradle 分析器也将创建差异火焰图。

### Gradle 构建扫描

[Gradle 构建扫描](https://gradle.com)是一个强大的工具，可以调查构建的结构并快速找到瓶颈。您可以使用时间线视图来查看运行了哪些任务，它们花费了多长时间，它们是否被缓存，构建的并行化程度如何等等。性能选项卡将向您展示配置时间的详细信息以及如何加快构建的其他提示。

为了创建您的构建的构建扫描，请使用 `--profile buildscan`。 构建扫描的URL在控制台上报告，也可以在 `profile-out/profile.log` 中获取到。 

### Async Profiler

Async profiler 在 Linux 和 MacOS 上提供低开销的 CPU，allocation 和 perf event 的采样。它还正确处理本地方法调用，使其在这些操作系统上比 JFR 更合适。

您可以通过 `--profile async-profiler` 来使用 async profiler。默认情况下会分析 CPU 使用情况，并提供一些合理的默认设置。这些设置可以使用下面列出的各种命令行选项进行配置。

或者，您也可以使用 `--profile async-profiler-heap` 来分析堆分配，并使用一些合理的默认设置。

最后，您也可以使用 `--profile async-profiler-all` 来分析 cpu、heap allocation 和锁，并使用一些合理的默认设置。

默认情况下，Async profiler在尚未可用的情况下将从 [Github](https://github.com/jvm-profiling-tools/async-profiler/releases) 下载并安装 。

输出文件的内容是您代码的代码调用树和热点的火焰图和冰柱图。

以下选项都支持，这些选项与 Async profiler 的选项非常相似。可以查看它的 readme 了解有关每个选项的更多信息：

- `--async-profiler-event`： 需要采样的 event, 例如 `cpu`，`wall`，`lock` 或 `alloc`。 默认是 `cpu`。可以多次使用此参数用来分析多个 event。
- `--async-profiler-count`：聚合事件数据时使用的 count。 `samples` 或 `total`。`total` 对 allocation 分析特别有用。默认是 `samples`. 对应 Async profiler 的 `--samples` 和 `--total` 命令行选项。
- `--async-profiler-interval`：采样分析的间隔时间，默认为 10_000_000 （10 ms）。
- `--async-profiler-alloc-interval`：allocation 分析的大小采样间隔（以字节为单位），默认为10字节。对应 Async profiler 的 `--alloc` 命令行选项。
- `--async-profiler-lock-threshold`：以纳秒为单位锁定探查阈值，默认值为250微秒。对应 Async profiler 的 `--lock` 命令行选项。
- `--async-profiler-stackdepth`：最大堆栈深度。如果深度递归配置文件太大，则降低这个深度。默认为2048。
- `--async-profiler-system-threads`：是否在 profile 中显示像 GC 和 JIT 编译的系统线程。通常会使它们更难阅读，但是如果您怀疑该区域有问题，会很有用。默认为 `false`。

您还可以使用 `ASYNC_PROFILER_HOME` 环境变量或 `--async-profiler-home` 命令行选项指向 Async profiler 的安装目录。

### JProfiler

JProfiler 是一个强大的商业分析器，它提供采样和检测功能。您可以在 JProfiler UI 中定制它的设置，然后指示 Gradle profiler 使用这些设置来完全控制您想要调查的内容。例如，您可以按参数分割对依赖项解析规则的调用，以确定该规则对于特定依赖项是否很慢。

您可以通过 `--profile jprofiler` 来使用 JProfiler。

默认情况下，这将使用 JProfiler 的 CPU 采样。JProfiler 支持其他几个选项：

- 通过添加 `--jprofiler-config sampling-all`（默认仅对包含 `gradle` 的包进行采样）启用 CPU 采样的所有方法
- 通过添加 `--jprofiler-config instrumentation` 切换到 CPU 检测
- 通过添加 `--jprofiler-alloc` 启用 memory allocation 记录
- 通过添加 `--jprofiler-monitors` 启用 monitor 使用情况
- 通过添加 `--jprofiler-probes:<probe ids, separated by comma>`（例如 `--jprofiler-probes builtin.FileProbe`）启用 probes
- 通过添加 `--jprofiler-heapdump` 启用在构建后 heap dump
- 通过添加 `--jprofiler-session <sessionId>` 使用特定的 profiler session （用于完全控制过滤器、采样间隔等）
- 通过添加 `--jprofiler-home /path/to/jprofiler` 使用不同的 JProfiler

### YourKit

YourKit 是一个强大的商业 profiler，它提供采样和检测功能。它目前在 Gradle profiler 中的集成是有限的，例如缺少对 probes 和其他自定义设置的支持。如果您正在使用 YourKit，并且希望看到更好的支持，欢迎提出 pull request。

为了能够使用 YourKit，请确定已经设置了 `YOURKIT_HOME` 环境变量，然后再使用 `--profile yourkit` 选项。默认将会使用 YourKit 的 CPU 采样检测。

您可以通过使用 `--profile yourkit-tracing` 选项切换到 CPU trace。您可以通过使用 `--profile yourkit-heap` 选项切换到 memory allocation 分析。使用采样或 memory allocation 时，所有 probes 都是禁用的。

### Java Flight Recorder

JFR 提供了一个低开销的 CPU，allocation，IO 等待和锁分析，并支持所有主流操作系统上运行。
它从 Java7 开始在 Oracle JDK 上可用，从 Java11 开始在 OpenJDK 上可用（确保您至少有[11.0.3](https://bugs.openjdk.java.net/browse/JDK-8219347)）。

据我们所知，它是 Windows 唯一的低开销分配 profiler。然而，请注意它的缺点，例如，它不会对本地方法调用进行采样，因此，如果您的代码执行大量系统调用（如读取文件），您将得到误导性的 CPU 结果。 

您将获得 JFR 文件和火焰图可视化数据，这比 Java Mission Control UI 更容易理解。

通过添加 `--profile jfr` 选项使用 JFR 进行分析。您可以使用 `--jfr-settings` 更改 profiler 配置，指定 `.jfc` 文件的路径或内置模板（如 `profile`）的名称。

### Heap dump

要在每个度量的构建结束时捕获 heap dump，请添加 `--profile heap-dump` 选项。您可以将其与其他 `--profile` 选项一起使用。

### Chrome Trace

Chrome traces 是一种低级事件 dump（例如，正在评估的项目、正在运行的任务等）。当您不能创建构建扫描，但需要查看构建的整体结构时，它们很有用。它还显示 CPU 负载、内存使用和 GC 活动。使用 chrome-trace 需要Gradle 3.3+。

添加 `--profile chrome-trace` 选项然后在 Google Chrome 中输入 chrome://tracing 后打开结果文件。

## 命令行选项

- `--project-dir`：需要构建的工程目录（必须）。
- `--benchmark`：对构建进行基准测试。多次运行构建，并将结果写入 CSV 文件。
- `--profile <profiler>`：通过特定的 profiler 分析构建。参考上面提到的每个 profiler 的细节。
- `--output-dir <dir>`：输出结果的目录。默认值是 `profile-out`。如果输出目录已经存在，它会一直找到一个不存在的 `profile-out-<index>` 文件夹。
- `--warmups`：指定每个场景要运行的预热构建次数。分析时默认为2，基准测试时默认为6，不使用预热 daemon 进程时默认为1。
- `--iterations`：指定每个场景要运行的构建次数。分析默认为1，基准测试默认为10。
- `--bazel`：基准场景使用 Bazel 而不是 Gradle。默认情况下只运行 Gradle。您不能使用此工具分析 Bazel 构建。
- `--buck`：基准场景使用 Buck 而不是 Gradle。默认情况下只运行 Gradle。您不能使用此工具分析 Buck 构建。
- `--maven`：基准场景使用 Maven 而不是 Gradle。默认情况下只运行 Gradle。您不能使用此工具分析 Maven 构建。

以下命令行选项仅适用于度量 Gradle 构建时：

- `--gradle-user-home`：Gradule user home。默认为 `<project-dir>/gradle-user-home`，以将性能测试与其他构建隔离。
- `--gradle-version <version>`：指定用于运行构建的 Gradle 版本或安装位置，覆盖构建的默认版本。通过为每个版本使用一次此选项，可以指定多个版本。
- `--no-daemon`：使用带有 `--no-daemon` 选项的 `gradle` 命令行客户端来运行构建。默认是使用 Gradle tooling API 和 Gradle daemon。
- `--cold-daemon`： 使用冷 daemon 进程（刚刚启动的 daemon 进程）而不是温 daemon 进程（已经运行了一些构建的 daemon 进程）。默认是使用一个温 daemon 进程。
- `--cli`：使用 `gradle` 命令行来运行构建。默认是使用 Gradle tooling api 和 Gradle daemon 进程。
- `--measure-gc`：度量 GC 时间。仅支持 Gradle 6.1+。
- `--measure-config-time`：度量一些关于配置时间的额外细节，仅支持 Gradle 6.1+。
- `--measure-build-op`：另外度量在给定的构建操作中花费的累积时间，仅支持 Gradle 6.1+。
- `-D<key>=<value>`：在运行构建时定义一个系统属性，覆盖构建的默认值。
- `--studio-install-dir`：Android Studio 安装目录。当度量 Android Studio sync 的时候必需。
- `--studio-sandbox-dir`：Android Studio 沙盒目录。建议使用它，因为它将 Android Studio 进程与其他 Android Studio 进程隔离。默认情况下，这将设置为 `<output-dir>/studio-sandbox`。如果您希望 Android Studio 保留旧数据（例如索引），您应该设置并重用自己的文件夹。
- `--no-studio-sandbox`：不使用 Android Studio 沙箱，而是使用默认的 Android Studio 文件夹来存放 Android Studio 数据。
- `--no-diffs`：不生成差异火焰图。

## 高级分析场景

使用 `--scenario-file` 来提供一个场景文件用以定义要进行基准测试或配置的更复杂的场景。场景文件以 [Typesafe config](https://github.com/typesafehub/config) 格式定义。

场景文件定义了一个或多个场景。当运行 `gradle-profiler` 时，您可以通过在命令行中指定它的名称来选择运行哪些场景。例如：

    > gradle-profiler --benchmark --scenario-file performance.scenarios clean_build

举例：

    # 当在命令行上没有指定场景时，通过这里可以指定要使用的场景
    default-scenarios = ["assemble"]
    
    # 场景按字母顺序运行
    assemble {
        # 在报告中显示一个稍微更容易读懂的标题
        title = "Assemble"
        # 运行 'assemble' task
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
        run-using = tooling-api // 可以为 "cli" 或 "tooling-api"
        daemon = warm // 可以为 "warm"，"cold" 或 "none"
        measured-build-ops = ["org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType"] // 参考 --measure-build-op

        buck {
            targets = ["//thing/res_debug"]
            type = "android_binary" // 可以为 Buck 构建规则或 "all"
        }

        warm-ups = 10
    }
    ideaModel {
        title = "IDEA model"
        # 获取 IDEA tooling model
        tooling-api {
            model = "org.gradle.tooling.model.idea.IdeaProject"
        }
        # 也可以运行任务
        # tasks = ["assemble"]
    }
    toolingAction {
        title = "IDEA model"
        # 获取 IDEA tooling model
        tooling-api {
            action = "org.gradle.profiler.toolingapi.FetchProjectPublications"
        }
        # 也可以运行任务
        # tasks = ["assemble"]
    }
    androidStudioSync {
        title = "Android Studio Sync"
        # 度量一次 Android studio sync
        # 注意：需要 Android Studio Bumblebee (2021.1.1) 或更高
        android-studio-sync {
            # 覆写默认的 Android Studio jvm 参数
            # studio-jvm-args = ["-Xms256m", "-Xmx4096m"]
        }
    }

值是可选的，默认为命令行提供的值或构建中定义的值。

### 分析增量构建

在每次构建前，一个场景可以定义应该被应用于 source 的更改。您可以使用它来对一个增量构建进行基准或分析。以下的改变是可用的：

- `apply-build-script-change-to`：向 Groovy 或 Kotlin DSL 构建脚本、init 脚本或 settings 脚本添加一条语句。每次迭代添加一个新语句，并删除前一次迭代添加的语句。
- `apply-project-dependency-change-to`：向 Groovy 或 Kotlin DSL 构建脚本添加 project 依赖项。每个迭代都添加一个新的项目组合作为依赖项，并删除前一个迭代添加的项目。
- `apply-abi-change-to`：向 Java 或 Kotlin class 添加一个 public 方法。每次迭代都会添加一个新方法，并删除前一次迭代添加的方法。
- `apply-non-abi-change-to`：修改一个在 Java 或 Kotlin class 中 public 方法的方法体。
- `apply-h-change-to`：在 C/C++ 头文件中添加一个函数。每次迭代都会添加一个新的函数声明，并删除前一次迭代添加的函数。
- `apply-cpp-change-to`：在 C/C++ source 文件中添加一个函数。每次迭代添加一个新函数，并删除前一次迭代添加的函数。
- `apply-property-resource-change-to`：在 properties 文件中添加一个条目。每次迭代都会添加一个新条目，并删除前一次迭代添加的条目。
- `apply-android-resource-change-to`：添加一个字符串资源到一个 Android 资源文件。每次迭代添加一个新资源，并删除前一次迭代添加的资源。
- `apply-android-resource-value-change-to`：更改一个在 Android 资源文件中的字符串资源在。
- `apply-android-layout-change-to`：在 Android 布局文件中添加一个包含 id 的隐藏 View。支持传统布局以及以 ViewGroup 作为根元素的 Databinding 布局。
- `apply-kotlin-composable-change-to`：向一个 Kotlin 文件添加一个 `@Composable` 方法。
- `clear-build-cache-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除构建缓存的内容。
- `clear-gradle-user-home-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除 Gradle user home 目录的内容。这个 mutator 保留 Gradle user home 中的 `wrapper` 缓存，因为在那个位置下载的 wrapper 是用来运行 Gradle 的。要求使用 `none` daemon 选项用于 `CLEANUP` 或`BUILD`。
- `clear-configuration-cache-state-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除 `.gradle/configuration-cache-state` 目录的内容。
- `clear-project-cache-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除 `.gradle` 和 `buildSrc/.gradle` 项目缓存目录的内容。
- `clear-transform-cache-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除 transform 缓存的内容。
- `clear-jars-cache-before`：在场景执行之前（`SCENARIO`）、清理之前（`CLEANUP`）或构建执行之前（`BUILD`）删除 instrumented jars 缓存的内容。
- `clear-android-studio-cache-before`：由于 Android Studio 在清理之前（`CLEANUP`）的细节上不支持，所以仅在场景执行之前（`SCENARIO`）、构建执行之前（`BUILD`）使 Android Studio 缓存失效。注意：清理 Android Studio 缓存仅在 Android Studio sync（`android-studio-sync`）的时候才会进行。
- `git-checkout`：切换到指定的 commit 用于构建，指定另一个不同的用于清理。
- `git-revert`：在构建之前还原给定的提交 commit，并在构建之后重置它。
- `iterations`：实际度量的构建次数。
- `jvm-args`：通过 `org.gradle.jvmargs` 设置或重写在 gradle.properties 中的 jvm 参数。
- `show-build-cache-size`：在场景执行之前，以及每次清理和构建之后，显示构建缓存中的文件数量和它们的大小。
- `warm-ups`：测量前要进行的预热次数。

它们可以像这样添加到场景文件中::

    incremental_build {
        tasks = ["assemble"]

        apply-build-script-change-to = "build.gradle.kts"
        apply-project-dependency-change-to {
            files = ["build.gradle"]
            # 默认的 dependency-count 是3
            # Gradle Profiler 会通过生成一些附加的项目来模拟项目依赖关系的变化，然后在每次迭代之前将项目依赖关系的组合添加到每个未生成的 subproject 中。
            # profiler 将生成最小数量的 subproject，以允许在每次迭代中使用唯一的依赖组合。
            # 注意：生成的项目数计算为二项式系数: "`x` 取 `dependency-count` = `循环次数 * 文件数`"，其中生成的 project 数量为 `x`。
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
        git-checkout = {
            cleanup = "efb43a1"
            build = "master"
        }
        git-revert = ["efb43a1"]
        jvm-args = ["-Xmx2500m", "-XX:MaxMetaspaceSize=512m"]
    }

### 与其他构建工具进行比较

通过在场景文件中指定它们的等价调用，你可以将 Gradle 与 Bazel、Buck 和 Maven进行比较。只支持基准模式。

#### Maven

    > gradle-profiler --benchmark --maven clean_build

    clean_build {
        tasks = ["build"]
        cleanup-tasks = ["clean"]
        maven {
            # 如果为空，将会从 MAVEN_HOME 环境变量推导
            home = "/path/to/maven/home"
            targets = ["clean", "build"]
        }
    }

#### Bazel

    > gradle-profiler --benchmark --bazel build_some_target

    build_some_target {
        tasks = ["assemble"]

        bazel {
            # 如果为空，将会从 BAZEL_HOME 环境变量推导
            home = "/path/to/bazel/home"
            targets = ["build" "//some/target"]
        }
    }
    
#### Buck

    > gradle-profiler --benchmark --buck build_binaries

    build_binaries {
        tasks = ["assemble"]

        buck {
            # 如果为空，将会从 BUCK_HOME 环境变量推导
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