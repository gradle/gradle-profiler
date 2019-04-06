package org.gradle.profiler;

public class GradleBuildInvoker extends BuildInvoker {
    static final GradleBuildInvoker ToolingApi = new GradleBuildInvoker() {
        @Override
        public String toString() {
            return "Tooling API";
        }

        @Override
        public boolean isReuseDaemon() {
            return true;
        }

        @Override
        public GradleBuildInvoker withColdDaemon() {
            return ToolingApiColdDaemon;
        }
    };
    static final GradleBuildInvoker ToolingApiColdDaemon = new GradleBuildInvoker() {
        @Override
        public String toString() {
            return "Tooling API with cold daemon";
        }

        @Override
        public boolean isColdDaemon() {
            return true;
        }
    };
    static final GradleBuildInvoker Cli = new GradleBuildInvoker() {
        @Override
        public String toString() {
            return "`gradle` command";
        }

        @Override
        public boolean isReuseDaemon() {
            return true;
        }

        @Override
        public GradleBuildInvoker withColdDaemon() {
            return CliColdDaemon;
        }
    };
    static final GradleBuildInvoker CliNoDaemon = new GradleBuildInvoker() {
        @Override
        public String toString() {
            return "`gradle` command with --no-daemon";
        }

    };
    static final GradleBuildInvoker CliColdDaemon = new GradleBuildInvoker() {
        @Override
        public String toString() {
            return "`gradle` command with cold daemon";
        }

        @Override
        public boolean isColdDaemon() {
            return true;
        }
    };

    public GradleBuildInvoker withColdDaemon() {
        if (isColdDaemon()) {
            return this;
        }
        throw new UnsupportedOperationException();
    }

    public boolean isReuseDaemon() {
        return false;
    }

    public boolean isColdDaemon() {
        return false;
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
