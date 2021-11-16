package org.gradle.profiler.mutations.support;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProjectCombinations {

    private final List<String> projectNames;
    private final Iterator<Set<String>> combinations;

    public ProjectCombinations(List<String> projectNames, Set<Set<String>> combinations) {
        this.projectNames = Collections.unmodifiableList(projectNames);
        this.combinations = combinations.iterator();
    }

    public List<String> getProjectNames() {
        return projectNames;
    }

    public Set<String> getNextCombination() {
        return combinations.next();
    }
}
