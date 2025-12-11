package org.gradle.profiler.fixtures.compatibility;

import org.gradle.profiler.fixtures.multitest.AbstractMultiTestInterceptor;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;


public abstract class AbstractContextualMultiVersionTestInterceptor<T> extends AbstractMultiTestInterceptor {

    public static final String VERSIONS_SYSPROP_NAME = "org.gradle.integtest.versions";

    public AbstractContextualMultiVersionTestInterceptor(Class<?> target) {
        super(target);
    }

    protected abstract Collection<? extends T> getAllVersions();

    protected abstract Collection<? extends Execution> createExecutionsFor(T version);

    protected boolean isAvailable(T version) {
        return true;
    }

    @Override
    protected void createExecutions() {
        String versions = System.getProperty(VERSIONS_SYSPROP_NAME, VersionCoverage.DEFAULT.getSelector());
        VersionCoverage versionCoverage = VersionCoverage.from(versions);
        createExecutionsForContext(versionCoverage);
    }

    private Collection<T> getQuickVersions() {
        T last = getLastAvailable(getAllVersions());
        return last == null ? emptyList() : singleton(last);
    }

    private Collection<T> getPartialVersions() {
        Collection<? extends T> allVersions = getAllVersions();
        Set<T> partialVersions = new HashSet<>();
        T firstAvailable = getFirstAvailable(allVersions);
        if (firstAvailable != null) {
            partialVersions.add(firstAvailable);
        }
        T lastAvailable = getLastAvailable(allVersions);
        if (lastAvailable != null) {
            partialVersions.add(lastAvailable);
        }
        return partialVersions;
    }

    private Collection<T> getAvailableVersions() {
        return getAllVersions().stream().filter(this::isAvailable).collect(Collectors.toSet());
    }

    @Nullable
    private T getFirstAvailable(Collection<? extends T> versions) {
        for (T next : versions) {
            if (isAvailable(next)) {
                return next;
            }
        }
        return null;
    }

    @Nullable
    private T getLastAvailable(Collection<? extends T> versions) {
        T lastAvailable = null;

        for (T next : versions) {
            if (isAvailable(next)) {
                lastAvailable = next;
            }
        }

        return lastAvailable;
    }

    private void createExecutionsForContext(VersionCoverage versionCoverage) {
        Set<T> versionsUnderTest = new HashSet<>();
        switch (versionCoverage) {
            case DEFAULT:
            case LATEST:
                versionsUnderTest.addAll(getQuickVersions());
                break;
            case PARTIAL:
                versionsUnderTest.addAll(getPartialVersions());
                break;
            case FULL:
                versionsUnderTest.addAll(getAvailableVersions());
                break;
            default:
                throw new IllegalArgumentException("Coverage context must be provided, was '" + versionCoverage + "'");
        }

        for (T version : versionsUnderTest) {
            for (Execution execution : createExecutionsFor(version)) {
                add(execution);
            }
        }
    }
}
