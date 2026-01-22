package org.gradle.profiler.gradle;

import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleClient;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.gradle.CliGradleClient;
import org.gradle.profiler.gradle.ToolingApiGradleClient;
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
