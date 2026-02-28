package org.gradle.profiler.gradle;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ide.IdeGradleClient;
import org.gradle.profiler.ide.invoker.IdeGradleScenarioDefinition.IdeGradleBuildConfiguration;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import static org.gradle.profiler.ide.IdeGradleClient.CleanCacheMode.BEFORE_BUILD;
import static org.gradle.profiler.ide.IdeGradleClient.CleanCacheMode.BEFORE_SCENARIO;
import static org.gradle.profiler.ide.IdeGradleClient.CleanCacheMode.NEVER;

/**
 * Specifies a client to be used to invoke Gradle builds.
 */
public enum GradleClientSpec {
    ToolingApi("Tooling API"){
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
    Ide("IDE sync") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new IdeGradleClient((IdeGradleBuildConfiguration) buildConfiguration, invocationSettings, NEVER);
        }
    },
    IdeCleanCacheBeforeBuild("IDE sync with clean cache before build") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new IdeGradleClient((IdeGradleBuildConfiguration) buildConfiguration, invocationSettings, BEFORE_BUILD);
        }
    },
    IdeCleanCacheBeforeScenario("IDE sync with clean cache before scenario") {
        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new IdeGradleClient((IdeGradleBuildConfiguration) buildConfiguration, invocationSettings, BEFORE_SCENARIO);
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
}
