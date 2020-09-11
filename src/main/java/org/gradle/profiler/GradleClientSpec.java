package org.gradle.profiler;

import org.gradle.profiler.studio.StudioGradleInvoker;
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
        public GradleInvoker createInvoker(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new ToolingApiInvoker(projectConnection);
        }
    };

    public static final GradleClientSpec GradleCli = new GradleClientSpec() {
        @Override
        public String toString() {
            return "`gradle` command";
        }

        @Override
        public GradleInvoker createInvoker(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new CliInvoker(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), true);
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
        public GradleInvoker createInvoker(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new CliInvoker(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), false);
        }
    };

    public static final GradleClientSpec AndroidStudio = new GradleClientSpec() {
        @Override
        public String toString() {
            return "Android Studio";
        }

        @Override
        public GradleInvoker createInvoker(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection) {
            return new StudioGradleInvoker();
        }
    };

    public boolean isUsesDaemon() {
        return true;
    }

    public abstract GradleInvoker createInvoker(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings, ProjectConnection projectConnection);
}
