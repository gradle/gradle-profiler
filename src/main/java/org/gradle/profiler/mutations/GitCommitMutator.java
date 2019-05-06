package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.util.function.Supplier;

public class GitCommitMutator extends AbstractGitMutator {

	private final String commit;

	private String originalBranch;

	public GitCommitMutator(File projectDir, String commit) {
		super(projectDir);
		this.commit = commit;
	}

	@Override
	public void beforeScenario() {
        System.out.println("> Checking out commit " + commit);
        originalBranch = new CommandExec().inDir(projectDir).runAndCollectOutput("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
        try {
            new CommandExec().inDir(projectDir).run("git", "checkout", commit, "--quiet");
        } catch (RuntimeException e) {
            throw new UnsupportedOperationException("Cannot checkout " + commit + " there are unsaved changes. Please commit or stash these changes and try again");
        }
	}

	@Override
    public void afterScenario() {
        new CommandExec().inDir(projectDir).run("git", "checkout", originalBranch, "--quiet");
        resetGit();
    }

    public static class Configurator implements BuildMutatorConfigurator {
		@Override
		public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
			String commit = ConfigUtil.string(scenario, key);
			return () -> new GitCommitMutator(projectDir, commit);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + commit + ")";
	}
}
