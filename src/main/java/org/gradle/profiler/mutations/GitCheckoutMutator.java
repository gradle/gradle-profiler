package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;

import java.io.File;

public class GitCheckoutMutator extends AbstractGitMutator {

	private final String cleanup;
	private final String build;
	private String original;

	public GitCheckoutMutator(File projectDir, String cleanup, String build) {
		super(projectDir);
		this.cleanup = cleanup;
		this.build = build;
	}

	@Override
	public void beforeScenario(ScenarioContext context) {
		resetGit();
		original = getCurrentCommit();
	}

	@Override
	public void beforeCleanup(BuildContext context) {
		if (cleanup != null) {
			checkout(cleanup);
		}
	}

	@Override
	public void beforeBuild(BuildContext context) {
		if (build != null) {
			checkout(build);
		}
	}

	@Override
	public void afterBuild(BuildContext context, Throwable error) {
		if (error == null) {
			checkout(original);
		} else {
			System.out.println("> Not checking out original Git commit because of error during build");
		}
	}

	private String getCurrentCommit() {
		return new CommandExec().inDir(projectDir).runAndCollectOutput("git", "rev-parse", "HEAD").trim();
	}

	private void checkout(String target) {
		System.out.println("> Checking out " + target);
		new CommandExec().inDir(projectDir).run("git", "checkout", target);
	}

	public static class Configurator implements BuildMutatorConfigurator {
		@Override
		public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
			Config config = scenario.getConfig(key);
			String cleanup = ConfigUtil.string(config, "cleanup", null);
			String build = ConfigUtil.string(config, "build", null);
			if (build == null) {
				throw new IllegalArgumentException("No git-checkout target specified for build");
			}
			return new GitCheckoutMutator(settings.getProjectDir(), cleanup, build);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(cleanup: " + cleanup + ", build: " + build + ")";
	}
}
