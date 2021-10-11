package org.gradle.profiler.mutations


import com.typesafe.config.ConfigFactory
import org.gradle.profiler.BuildInvoker
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.report.CsvGenerator

class ExecuteCommandBuildMutatorTest extends AbstractMutatorTest {


    def static createInvocationSettings(BuildInvoker buildInvoker, String target) {
        return new InvocationSettings(new File("."),
            null,
            true,
            new File("outputdir"),
            buildInvoker,
            false,
            new File("scenariofile"),
            Collections.emptyList(),
            Arrays.asList(target),
            Collections.emptyMap(),
            new File("gradlehome"),
            new File("studioinstall"),
            5,
            10,
            false,
            false,
            Collections.emptyList(),
            CsvGenerator.Format.LONG,
            "benchmarkTitle",
            new File("buildLog")
        )
    }

    def invoker = new CommandInvoker() {
        private List<List<String>> commands

        @Override
        int execute(final List<String> commands) throws InterruptedException, IOException {
            this.commands.add(commands)
            return 0
        }

        void reset() {
            commands = new ArrayList<>()
        }
    }

    def setup() {
        invoker.reset()
    }

    def "executes the commands before scenario starts when Schedule is SCENARIO"() {
        def mutator = new ExecuteCommandBuildMutator(ExecuteCommandBuildMutator.ExecuteCommandSchedule.SCENARIO,
            Arrays.asList("echo", "Hello World"),
            invoker)

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        invoker.commands.size() == 1
        invoker.commands.get(0) == Arrays.asList("echo", "Hello World")
    }

    def "doesn't execute the commands before scenario starts when Schedule is BUILD"() {
        def mutator = new ExecuteCommandBuildMutator(ExecuteCommandBuildMutator.ExecuteCommandSchedule.BUILD,
            Arrays.asList("echo", "Hello World"),
            invoker)

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        invoker.commands.isEmpty()
    }

    def "Configure ExecuteCommandBuildMutator from scenario"() {
        def mutator = new ExecuteCommandBuildMutator(ExecuteCommandBuildMutator.ExecuteCommandSchedule.BUILD,
            Arrays.asList("echo", "Hello World"),
            invoker)

        when:
        mutator.beforeScenario(scenarioContext)
        then:
        invoker.commands.isEmpty()
    }

    def "execute the commands before scenario starts when Schedule is BUILD"() {
        def mutator = new ExecuteCommandBuildMutator(ExecuteCommandBuildMutator.ExecuteCommandSchedule.BUILD,
            Arrays.asList("echo", "Hello World"),
            invoker)

        when:
        mutator.beforeBuild(buildContext)
        then:
        invoker.commands.size() == 1
        invoker.commands.get(0) == Arrays.asList("echo", "Hello World")
    }

    def "Given Gradle scenario verify that the configuration creates a mutator that executes"() {
        given:
        def config = ConfigFactory.parseString(
            """
                  testScenario {
                      tasks = ["tasks"]
                      clear-build-cache-before = SCENARIO
                      gradle-args = [
                        "-Pbuild.cache.local.enabled=true"
                      ]
                      execute-command-before = [
                        {
                          schedule = SCENARIO
                          commands = ["gsutil", "version"]
                        },
                        {
                          schedule = BUILD
                          commands = ["/bin/bash", "-c", "echo helloworld"]
                        }
                      ]
                  }
               """
        )

        def mutator = new ExecuteCommandBuildMutator.Configurator(invoker).configure(
            config.getConfig("testScenario"),
            "testScenario",
            createInvocationSettings(null, "testScenario"),
            "execute-command-before"
        )
        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)
        then:
        invoker.commands.size() == 2
        invoker.commands.get(0) == Arrays.asList("gsutil", "version")
        invoker.commands.get(1) == Arrays.asList("/bin/bash", "-c", "echo helloworld")
    }
    
}
