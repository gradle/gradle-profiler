package org.gradle.profiler.mutations.support;

import com.google.common.collect.Iterables;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProjectCombinations {

    private final List<String> projectNames;
    private final Iterator<Set<String>> combinations;
    private final String salt;

    public ProjectCombinations(String salt, List<String> projectNames, Set<Set<String>> combinations) {
        this.salt = salt;
        this.projectNames = Collections.unmodifiableList(projectNames);
        this.combinations = Iterables.cycle(combinations).iterator();
    }

    public String getSalt() {
        return salt;
    }

    public List<String> getProjectNames() {
        return projectNames;
    }

    public Set<String> getNextCombination() {
        return combinations.next();
    }
}
