package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.ConfigUtil;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GitRevertMutator extends AbstractGitMutator {

	private final List<String> commits;

	public GitRevertMutator(File projectDir, List<String> commits) {
		super(projectDir);
		this.commits = commits;
	}

	@Override
	public void beforeScenario(ScenarioContext context) {
		resetGit();
	}

	@Override
	public void beforeBuild(BuildContext context) {
        revertCommits();
	}

	@Override
	public void afterBuild(BuildContext context, Throwable error) {
		if (error == null) {
			abortRevert();
			resetGit();
		} else {
			System.out.println("> Not resetting Git because of error during build");
		}
	}

	private void revertCommits() {
        System.out.println("> Reverting Git commit(s) " + commits.stream().collect(Collectors.joining(", ")));
        new CommandExec().inDir(projectDir).run("git", "revert", "--quit");
        List<String> commandLine = new ArrayList<>();
        commandLine.addAll(Arrays.asList("git", "revert", "--no-commit"));
        commandLine.addAll(commits);
        new CommandExec().inDir(projectDir).run(commandLine);
    }

    private void abortRevert() {
        System.out.println("> Aborting revert");
        new CommandExec().inDir(projectDir).run("git", "revert", "--abort");
    }

    public static class Configurator implements BuildMutatorConfigurator {
		@Override
		public BuildMutator configure(Config scenario, String scenarioName, InvocationSettings settings, String key) {
			List<String> commits = ConfigUtil.strings(scenario, key);
			if (commits.isEmpty()) {
				throw new IllegalArgumentException("No commits specified for git-revert");
			}
			return new GitRevertMutator(settings.getProjectDir(), commits);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + commits.stream().collect(Collectors.joining(", ")) + ")";
	}
}
