package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public abstract class AbstractCleanupMutator implements BuildMutator {

    private final CleanupSchedule schedule;

    public AbstractCleanupMutator(CleanupSchedule schedule) {
        this.schedule = schedule;
    }

    @Override
    public void beforeBuild(BuildContext context) {
        if (schedule == CleanupSchedule.BUILD) {
            cleanup();
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        if (schedule == CleanupSchedule.SCENARIO) {
            cleanup();
        }
    }

    @Override
    public void beforeCleanup(BuildContext context) {
        if (schedule == CleanupSchedule.CLEANUP) {
            cleanup();
        }
    }

    abstract protected void cleanup();

    protected static void delete(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return;
            }

            for (File item : contents) {
                delete(item);
            }
        }

        try {
            tryHardToDelete(file.toPath());
        } catch (IOException ex) {
            throw new UncheckedIOException(String.format("Couldn't delete file '%s'", file), ex);
        }
    }

    private static void tryHardToDelete(Path path) throws IOException {
        try {
            Files.deleteIfExists(path);
            if (!Files.exists(path)) {
                return;
            }
        } catch (IOException ex) {
            // Continue
        }

        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // It mentions that there is a bug in the Windows JDK implementations that this is a valid
        // workaround for. I've been unable to find a definitive reference to this bug.
        // The thinking is that if this is good enough for Ant, it's good enough for us.
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        Files.deleteIfExists(path);
        if (Files.exists(path)) {
            throw new IOException(String.format("File '%s' still exists after repeated attempts to delete it", path));
        }
    }

    protected static abstract class Configurator implements BuildMutatorConfigurator {
        @Override
        public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
            CleanupSchedule schedule = ConfigUtil.enumValue(scenario, key, CleanupSchedule.class, null);
            if (schedule == null) {
                throw new IllegalArgumentException("Schedule for cleanup is not specified");
            }
            return () -> newInstance(scenario, scenarioName, projectDir, key, schedule);
        }

        protected abstract BuildMutator newInstance(Config scenario, String scenarioName, File projectDir, String key, CleanupSchedule schedule);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + schedule + ")";
    }

    public enum CleanupSchedule {
        SCENARIO, CLEANUP, BUILD
    }
}
