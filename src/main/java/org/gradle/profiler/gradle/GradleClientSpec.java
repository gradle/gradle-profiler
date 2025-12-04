package org.gradle.profiler.gradle;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.idea.IdeaGradleClient;
import org.gradle.profiler.idea.IdeaGradleScenarioDefinition;
import org.gradle.profiler.idea.IdeaSyncInvocationSettings;
import org.gradle.profiler.idea.ProfilerAgentJars;
import org.gradle.profiler.instrument.GradleInstrumentation;
import org.gradle.profiler.studio.StudioGradleClient;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition.StudioGradleBuildConfiguration;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import static org.gradle.profiler.studio.StudioGradleClient.CleanCacheMode.BEFORE_BUILD;
import static org.gradle.profiler.studio.StudioGradleClient.CleanCacheMode.BEFORE_SCENARIO;
import static org.gradle.profiler.studio.StudioGradleClient.CleanCacheMode.NEVER;

/**
 * Specifies a client to be used to invoke Gradle builds.
 */
public enum GradleClientSpec {
    ToolingApi("Tooling API") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            GradleConnector connector = GradleConnector.newConnector()
                .useInstallation(buildConfiguration.getGradleHome())
                .useGradleUserHomeDir(invocationSettings.getGradleUserHome().getAbsoluteFile());
            ProjectConnection projectConnection = connector.forProjectDirectory(invocationSettings.getProjectDir()).connect();
            return new ToolingApiGradleClient(projectConnection);
        }
    },
    GradleCli("`gradle` command") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), true, invocationSettings.getBuildLog());
        }
    },
    GradleCliNoDaemon("`gradle` command with --no-daemon") {
        @Override
        public boolean isUsesDaemon() {
            return false;
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), false, invocationSettings.getBuildLog());
        }
    },
    AndroidStudio("Android Studio") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new StudioGradleClient((StudioGradleBuildConfiguration) buildConfiguration, invocationSettings, NEVER);
        }
    },
    AndroidStudioCleanCacheBeforeBuild("Android Studio with clean cache before build") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new StudioGradleClient((StudioGradleBuildConfiguration) buildConfiguration, invocationSettings, BEFORE_BUILD);
        }
    },
    AndroidStudioCleanCacheBeforeScenario("Android Studio with clean cache before scenario") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new StudioGradleClient((StudioGradleBuildConfiguration) buildConfiguration, invocationSettings, BEFORE_SCENARIO);
        }
    },
    IdeaCleanCacheBeforeScenario("IntelliJ IDEA with clean cache before scenario") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return GradleClientSpec.createIdeaGradleClient(buildConfiguration, invocationSettings, IdeaGradleClient.CleanCacheMode.BEFORE_SCENARIO);
        }
    },
    IdeaCleanCacheBeforeBuild("IntelliJ IDEA with clean cache before build") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return GradleClientSpec.createIdeaGradleClient(buildConfiguration, invocationSettings, IdeaGradleClient.CleanCacheMode.BEFORE_BUILD);
        }
    };

    private final String title;

    GradleClientSpec(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }

    public boolean isUsesDaemon() {
        return true;
    }

    public abstract GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings);

    private static GradleClient createIdeaGradleClient(
        GradleBuildConfiguration buildConfiguration,
        InvocationSettings invocationSettings,
        IdeaGradleClient.CleanCacheMode cleanCacheMode
    ) {
        IdeaSyncInvocationSettings ideaSyncInvocationSettings = invocationSettings.getIdeaSyncInvocationSettings();
        if (ideaSyncInvocationSettings == null) {
            throw new RuntimeException("IDEA sync invocations settings must be provided");
        }

        ProfilerAgentJars profilerAgentJars = new ProfilerAgentJars(
            GradleInstrumentation.unpackPlugin("studio-agent").toPath().toAbsolutePath(),
            GradleInstrumentation.unpackPlugin("instrumentation-support").toPath().toAbsolutePath(),
            GradleInstrumentation.unpackPlugin("asm").toPath().toAbsolutePath(),
            GradleInstrumentation.unpackPlugin("client-protocol").toPath().toAbsolutePath()
        );
        IdeaGradleScenarioDefinition.IdeaGradleBuildConfiguration ideaGradleBuildConfiguration = (IdeaGradleScenarioDefinition.IdeaGradleBuildConfiguration) buildConfiguration;
        return new IdeaGradleClient(
            ideaGradleBuildConfiguration.getIdeaSyncScenarioDefinition(),
            ideaSyncInvocationSettings,
            profilerAgentJars,
            buildConfiguration.getGradleHome(),
            invocationSettings.getProjectDir().toPath(),
            cleanCacheMode
        );
    }
}
