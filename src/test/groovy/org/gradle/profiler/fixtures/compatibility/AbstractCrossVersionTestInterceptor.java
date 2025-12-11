package org.gradle.profiler.fixtures.compatibility;

public abstract class AbstractCrossVersionTestInterceptor<T> extends AbstractContextualMultiVersionTestInterceptor<T> {

    private static final String NAME_SUFFIX = "CrossVersion";

    protected AbstractCrossVersionTestInterceptor(
        Class<?> target,
        Class<?> baseTestClass,
        String additionalNameSuffix
    ) {
        super(target);

        String targetName = target.getSimpleName();
        String expectedSuffix = additionalNameSuffix + NAME_SUFFIX;
        if (!targetName.contains(expectedSuffix)) {
            throw new RuntimeException(String.format(
                "Tests that extend '%s' must follow naming conventions to allow for easier identification and filtering on CI.\n" +
                    "Please include '%s' in the name of the test class: '%s'\n",
                baseTestClass.getSimpleName(), expectedSuffix, targetName));
        }
    }

}
