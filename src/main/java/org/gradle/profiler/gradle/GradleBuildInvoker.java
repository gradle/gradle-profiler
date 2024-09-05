package org.gradle.profiler.gradle;

import org.gradle.profiler.BuildInvoker;

public class GradleBuildInvoker extends BuildInvoker {

    /**
     * Invokers should be public since they are used in the gradle/gradle project
     */
    public static final GradleBuildInvoker ToolingApi = new GradleBuildInvoker(GradleClientSpec.ToolingApi, GradleDaemonReuse.WarmDaemonOnly);
    public static final GradleBuildInvoker Cli = new GradleBuildInvoker(GradleClientSpec.GradleCli, GradleDaemonReuse.WarmDaemonOnly);
    public static final GradleBuildInvoker CliNoDaemon = new GradleBuildInvoker(GradleClientSpec.GradleCliNoDaemon, GradleDaemonReuse.ColdDaemonOnly) {
        @Override
        public String toString() {
            return "`gradle` command with --no-daemon";
        }
    };
    public static final GradleBuildInvoker AndroidStudio = new GradleBuildInvoker(GradleClientSpec.AndroidStudio, GradleDaemonReuse.WarmDaemonOnly);
    public static final GradleBuildInvoker AndroidStudioCleanCacheBeforeScenario = new GradleBuildInvoker(GradleClientSpec.AndroidStudioCleanCacheBeforeScenario, GradleDaemonReuse.WarmDaemonOnly);
    public static final GradleBuildInvoker AndroidStudioCleanCacheBeforeBuild = new GradleBuildInvoker(GradleClientSpec.AndroidStudioCleanCacheBeforeBuild, GradleDaemonReuse.ColdDaemonOnly);

    private final GradleClientSpec client;
    private final GradleDaemonReuse daemonReuse;

    private GradleBuildInvoker(GradleClientSpec client, GradleDaemonReuse daemonReuse) {
        this.client = client;
        this.daemonReuse = daemonReuse;
    }

    @Override
    public boolean allowsMutationBetweenBuilds() {
        // Warm daemons keep caches open and thus they cannot be removed
        return !isReuseDaemon();
    }

    @Override
    public String toString() {
        if (daemonReuse == GradleDaemonReuse.ColdDaemonOnly) {
            return client + " with cold daemon";
        } else {
            return client.toString();
        }
    }

    public GradleBuildInvoker withColdDaemon() {
        if (daemonReuse == GradleDaemonReuse.ColdDaemonOnly) {
            return this;
        }
        return new GradleBuildInvoker(client, GradleDaemonReuse.ColdDaemonOnly);
    }

    public GradleClientSpec getClient() {
        return client;
    }

    public GradleDaemonReuse getDaemonReuse() {
        return daemonReuse;
    }

    public boolean isDoesNotUseDaemon() {
        return !getClient().isUsesDaemon();
    }

    public boolean isReuseDaemon() {
        return getDaemonReuse() == GradleDaemonReuse.WarmDaemonOnly;
    }

    public boolean isShouldCleanUpDaemon() {
        return getDaemonReuse() != GradleDaemonReuse.WarmDaemonOnly;
    }

    @Override
    public int benchmarkWarmUps() {
        if (!isReuseDaemon()) {
            // Do not warm up the daemon if it is not being reused
            return 1;
        }
        return super.benchmarkWarmUps();
    }

    @Override
    public int profileWarmUps() {
        if (!isReuseDaemon()) {
            // Do not warm up the daemon if it is not being reused
            return 1;
        }
        return super.profileWarmUps();
    }
}
