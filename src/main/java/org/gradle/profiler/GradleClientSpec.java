package org.gradle.profiler;

import org.gradle.profiler.studio.StudioGradleClient;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import static org.gradle.profiler.studio.StudioGradleClient.SyncMode.CLEAN_CACHE_AND_SYNC;
import static org.gradle.profiler.studio.StudioGradleClient.SyncMode.SYNC_ONLY;

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
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            GradleConnector connector = GradleConnector.newConnector()
                .useInstallation(buildConfiguration.getGradleHome())
                .useGradleUserHomeDir(invocationSettings.getGradleUserHome().getAbsoluteFile());
            ProjectConnection projectConnection = connector.forProjectDirectory(invocationSettings.getProjectDir()).connect();
            return new ToolingApiGradleClient(projectConnection);
        }
    };

    public static final GradleClientSpec GradleCli = new GradleClientSpec() {
        @Override
        public String toString() {
            return "`gradle` command";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), true, invocationSettings.getBuildLog());
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
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new CliGradleClient(buildConfiguration, buildConfiguration.getJavaHome(), invocationSettings.getProjectDir(), false, invocationSettings.getBuildLog());
        }
    };

    public static final GradleClientSpec AndroidStudio = new GradleClientSpec() {
        @Override
        public String toString() {
            return "Android Studio";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new StudioGradleClient(buildConfiguration, invocationSettings, SYNC_ONLY);
        }
    };

    public static final GradleClientSpec AndroidStudioFirstSync = new GradleClientSpec() {
        @Override
        public String toString() {
            return "Android Studio first sync";
        }

        @Override
        public GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings) {
            return new StudioGradleClient(buildConfiguration, invocationSettings, CLEAN_CACHE_AND_SYNC);
        }
    };

    public boolean isUsesDaemon() {
        return true;
    }

    public abstract GradleClient create(GradleBuildConfiguration buildConfiguration, InvocationSettings invocationSettings);
}
