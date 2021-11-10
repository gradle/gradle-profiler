package org.gradle.profiler.mutations.support;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProjectCombinations {

    private final List<String> projectNames;
    private final Iterator<Set<String>> combinations;

    public ProjectCombinations(List<String> projectNames, Set<Set<String>> combinations) {
        this.projectNames = projectNames;
        this.combinations = combinations.iterator();
    }

    public List<String> getProjectNames() {
        return projectNames;
    }

    public Iterator<Set<String>> getCombinationsIterator() {
        return combinations;
    }
}
