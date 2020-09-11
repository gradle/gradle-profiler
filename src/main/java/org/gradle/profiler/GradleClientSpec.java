package org.gradle.profiler;

import org.gradle.profiler.studio.StudioGradleClient;
import org.gradle.tooling.ProjectConnection;

/**
 * Specifies a client to be used to invoke Gradle builds.
 */
public abstract class GradleClientSpec {
    public static final GradleClientSpec ToolingApi = new GradleClientSpec() {
        @Override
        public String toString() {
            return "Tooling API";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new ToolingApiGradleClient(projectConnection);
        }
    };

    public static final GradleClientSpec GradleCli = new GradleClientSpec() {
        @Override
        public String toString() {
            return "`gradle` command";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), true);
        }
    };

    public static final GradleClientSpec GradleCliNoDaemon = new GradleClientSpec() {
        @Override
        public String toString() {
            return "`gradle` command with --no-daemon";
        }

        @Override
        public boolean isUsesDaemon() {
            return false;
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), false);
        }
    };

    public static final GradleClientSpec AndroidStudio = new GradleClientSpec() {
        @Override
        public String toString() {
            return "Android Studio";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new StudioGradleClient();
        }
    };

    public boolean isUsesDaemon() {
        return true;
    }

    public abstract GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection);
}
