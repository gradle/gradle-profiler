package org.gradle.profiler.toolingapi;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.gradle.ProjectPublications;

import java.util.ArrayList;
import java.util.List;

public class FetchProjectPublications implements BuildAction<List<ProjectPublications>> {
    @Override
    public List<ProjectPublications> execute(BuildController controller) {
        GradleBuild buildModel = controller.getBuildModel();
        List<FetchForProject> actions = new ArrayList<>();
        collectProjects(buildModel, actions);
        for (GradleBuild build : buildModel.getEditableBuilds()) {
            collectProjects(build, actions);
        }
        System.out.println("-> run actions for " + actions.size() + " projects");
        return controller.run(actions);
    }

    private void collectProjects(GradleBuild build, List<FetchForProject> actions) {
        for (BasicGradleProject project : build.getProjects()) {
            actions.add(new FetchForProject(project));
        }
    }

    private static class FetchForProject implements BuildAction<ProjectPublications> {
        private final BasicGradleProject project;

        public FetchForProject(BasicGradleProject project) {
            this.project = project;
        }

        @Override
        public ProjectPublications execute(BuildController controller) {
            return controller.findModel(project, ProjectPublications.class);
        }
    }
}
