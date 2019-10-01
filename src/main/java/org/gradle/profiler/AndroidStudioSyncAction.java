package org.gradle.profiler;

import com.android.builder.model.AndroidProject;

import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;
import java.util.*;

/**
 * A mock-up of Android studio sync.
 */
public class AndroidStudioSyncAction implements BuildAction {

    private final String buildFlavor;
    private final boolean skipSourceGeneration;

    public AndroidStudioSyncAction(String buildFlavor, boolean skipSourceGeneration) {
        this.buildFlavor = buildFlavor;
        this.skipSourceGeneration = skipSourceGeneration;
    }

    @Override
    public String getShortDisplayName() {
        return "AS sync";
    }

    @Override
    public String getDisplayName() {
        return "simulate Android Studio sync";
    }

    @Override
    public boolean isDoesSomething() {
        return true;
    }

    @Override
    public void run(GradleInvoker buildInvoker, List<String> gradleArgs, List<String> jvmArgs) {
        gradleArgs = new ArrayList<>(gradleArgs);
        gradleArgs.add("-Dcom.android.build.gradle.overrideVersionCheck=true");
        gradleArgs.add("-Pandroid.injected.build.model.only=true");
        gradleArgs.add("-Pandroid.injected.build.model.only.versioned=3");
        gradleArgs.add("-Pandroid.builder.sdkDownload=true");

        List<String> tasks;
        if (skipSourceGeneration) {
            tasks = Collections.emptyList();
        } else {
            String taskName = String.join("", "generate", StringUtils.capitalize(buildFlavor), "Sources");
            tasks = Collections.singletonList(taskName);
        }
        buildInvoker.runToolingAction(tasks, gradleArgs, jvmArgs, new GetModel(), (builder) -> builder.addProgressListener(noOpListener()));
    }

    private static ProgressListener noOpListener() {
        return event -> {
            // Ignore, just measure the impact of receiving the events
        };
    }

    public static class GetModel implements org.gradle.tooling.BuildAction<Map<String, AndroidProject>>, Serializable {
        @Override
        public Map<String, AndroidProject> execute(BuildController controller) {
            GradleBuild build = controller.getBuildModel();
            Map<String, AndroidProject> result = new TreeMap<String, AndroidProject>();
            for (BasicGradleProject project : build.getProjects()) {
                AndroidProject androidProject = controller.findModel(project, AndroidProject.class);
                result.put(project.getPath(), androidProject);
            }
            return result;
        }
    }
}
