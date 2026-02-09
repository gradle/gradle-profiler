package org.gradle.profiler.fixtures.file;

import groovy.lang.Closure;
import org.gradle.profiler.fixtures.util.RetryUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;


/**
 * A JUnit rule which provides a handy unique temporary folder for the test,
 * which is kept on disk in case of test failures to allow for easier debugging
 * of integration tests.
 * <p>
 * "build/tmp/test-files/{shortenTestClass}/qqlj8"
 */
public class TestNameTestDirectoryProvider implements TestRule, TestDirectoryProvider {
    protected final TestFile root;
    private final String shortClassName;

    private static final int ALL_DIGITS_AND_LETTERS_RADIX = 36;
    private static final int MAX_RANDOM_PART_VALUE = Integer.parseInt("zzzzz", ALL_DIGITS_AND_LETTERS_RADIX);
    private static final Pattern WINDOWS_RESERVED_NAMES = Pattern.compile("(con)|(prn)|(aux)|(nul)|(com\\d)|(lpt\\d)", Pattern.CASE_INSENSITIVE);

    private TestFile dir;
    private boolean suppressCleanup = false;

    public TestNameTestDirectoryProvider(Class<?> klass) {
        this.root = new TestFile(new File("build/tmp/test-files"));
        this.shortClassName = shortenPath(klass.getSimpleName(), 16);
    }

    @Override
    public TestFile getTestDirectory() {
        if (dir == null) {
            dir = createUniqueTestDirectory();
        }
        return dir;
    }

    public TestFile file(Object... path) {
        return getTestDirectory().file(path);
    }

    public TestFile createFile(Object... path) {
        return file(path).createFile();
    }

    public TestFile createDir(Object... path) {
        return file(path).createDir();
    }

    @Override
    public void suppressCleanup() {
        suppressCleanup = true;
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new TestDirectoryCleaningStatement(base, description);
    }

    private void cleanup() {
        if (!suppressCleanup && dir != null && dir.exists()) {
            // Retry deleting for up to 10s
            RetryUtil.poll(() -> dir.forceDeleteDir());
        }
    }

    /**
     * Shorten a long name to at most {expectedMaxLength}, replace middle characters with "."
     * <p>
     * MyIntegrationTest => MyIntegra.Test
     */
    private String shortenPath(String longName, int expectedMaxLength) {
        if (longName.length() <= expectedMaxLength) {
            return longName;
        } else {
            return longName.substring(0, expectedMaxLength - 5) + "." + longName.substring(longName.length() - 4);
        }
    }

    private TestFile createUniqueTestDirectory() {
        while (true) {
            // Use a random prefix to avoid reusing test directories
            String randomPrefix = Integer.toString(ThreadLocalRandom.current().nextInt(MAX_RANDOM_PART_VALUE), ALL_DIGITS_AND_LETTERS_RADIX);
            if (WINDOWS_RESERVED_NAMES.matcher(randomPrefix).matches() || Character.isDigit(randomPrefix.charAt(0))) {
                // a project name starting with a digit may cause troubles
                continue;
            }
            TestFile dir = root.file(shortClassName, randomPrefix);
            if (dir.mkdirs()) {
                return dir;
            }
        }
    }

    public class TestDirectoryCleaningStatement extends Statement {
        private final Statement base;
        private final Description description;

        TestDirectoryCleaningStatement(Statement base, Description description) {
            this.base = base;
            this.description = description;
        }

        public void cleanup() {
            try {
                TestNameTestDirectoryProvider.this.cleanup();
            } catch (Exception e) {
                if (e instanceof FileDeletionException && suppressCleanupErrors(((FileDeletionException) e).getFile())) {
                    System.err.println(cleanupErrorMessage());
                    e.printStackTrace(System.err);
                } else {
                    throw new RuntimeException(cleanupErrorMessage(), e);
                }
            }
        }

        private boolean suppressCleanupErrors(File notDeletedFile) {
            LeaksFileHandles testAnnotation = description.getAnnotation(LeaksFileHandles.class);
            if (testAnnotation != null) {
                return evaluateCondition(testAnnotation, notDeletedFile);
            }
            LeaksFileHandles classAnnotation = testClass().getAnnotation(LeaksFileHandles.class);
            if (classAnnotation != null) {
                return evaluateCondition(classAnnotation, notDeletedFile);
            }
            return false;
        }

        @SuppressWarnings("rawtypes")
        private boolean evaluateCondition(LeaksFileHandles annotation, File notDeletedFile) {
            Class<? extends Closure> conditionType = annotation.value();
            if (conditionType == Closure.class) {
                return true;
            }
            try {
                Closure condition = conditionType.getConstructor(Object.class, Object.class).newInstance(null, null);
                condition.setDelegate(notDeletedFile);
                Object result = condition.call(notDeletedFile);
                return result instanceof Boolean ? (Boolean) result : result != null;
            } catch (Exception e) {
                throw new RuntimeException("Could not evaluate LeaksFileHandles condition: " + conditionType.getName(), e);
            }
        }

        private Class<?> testClass() {
            return description.getTestClass();
        }

        @Override
        public void evaluate() throws Throwable {
            base.evaluate();
            cleanup();
        }

        private String cleanupErrorMessage() {
            return "Couldn't delete test dir for `" + displayName() + "` (test is holding files open).";
        }

        private String displayName() {
            return description.getDisplayName();
        }
    }
}
