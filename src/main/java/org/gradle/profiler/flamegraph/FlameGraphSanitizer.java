package org.gradle.profiler.flamegraph;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Simplifies stacks to make flame graphs more readable.
 */
public class FlameGraphSanitizer {
    private static final Splitter STACKTRACE_SPLITTER = Splitter.on(";").omitEmptyStrings();
    private static final Joiner STACKTRACE_JOINER = Joiner.on(";");

    public static final SanitizeFunction COLLAPSE_BUILD_SCRIPTS = new ReplaceRegex(
            ImmutableMap.of(
                    Pattern.compile("build_[a-z0-9]+"), "build script",
                    Pattern.compile("settings_[a-z0-9]+"), "settings script"
            )
    );

    public static final SanitizeFunction COLLAPSE_GRADLE_INFRASTRUCTURE = new CompositeSanitizeFunction(
            new ChopPrefix("loadSettings"),
            new ChopPrefix("configureBuild"),
            new ChopPrefix("constructTaskGraph"),
            new ChopPrefix("executeTasks"),
            new ChopPrefix("org.gradle.api.internal.tasks.execution"),
            new ReplaceContainment(singletonList("org.gradle.api.internal.tasks.execution"), "task execution_[j]"),
            new ReplaceContainment(asList("DynamicObject", "Closure.call", "MetaClass", "MetaMethod", "CallSite", "ConfigureDelegate", "Method.invoke", "MethodAccessor", "Proxy", "ConfigureUtil", "Script.invoke", "ClosureBackedAction", "getProperty("), "dynamic invocation_[j]"),
            new ReplaceContainment(asList("BuildOperation", "PluginManager", "ObjectConfigurationAction", "PluginTarget", "PluginAware", "Script.apply", "ScriptPlugin", "ScriptTarget", "ScriptRunner", "ProjectEvaluator", "Project.evaluate"), "Gradle infrastructure_[j]")
    );

    public static final SanitizeFunction SIMPLE_NAMES = new ToSimpleName();

    public static final SanitizeFunction NORMALIZE_LAMBDA_NAMES = new NormalizeLambda();

    private final SanitizeFunction sanitizeFunction;

    public static FlameGraphSanitizer simplified(SanitizeFunction... additionalSanitizers) {
        ImmutableList.Builder<SanitizeFunction> builder = ImmutableList.builder();
        builder.add(additionalSanitizers);
        builder.add(
            FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS,
            FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE,
            FlameGraphSanitizer.SIMPLE_NAMES,
            FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES
        );
        builder.add(new CollapseDuplicateFrames());
        return new FlameGraphSanitizer(builder.build());
    }

    public static FlameGraphSanitizer raw(SanitizeFunction... additionalSanitizers) {
        ImmutableList.Builder<SanitizeFunction> builder = ImmutableList.builder();
        builder.add(additionalSanitizers);
        builder.add(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES);
        builder.add(new CollapseDuplicateFrames());
        return new FlameGraphSanitizer(builder.build());
    }

    public FlameGraphSanitizer(ImmutableList<SanitizeFunction> sanitizeFunctions) {
        this.sanitizeFunction = new CompositeSanitizeFunction(sanitizeFunctions);
    }

    public void sanitize(final File in, File out) {
        out.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(out));
             BufferedReader reader = Files.newBufferedReader(in.toPath())) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                int endOfStack = line.lastIndexOf(" ");
                if (endOfStack <= 0) {
                    continue;
                }
                String stackTrace = line.substring(0, endOfStack);
                String invocationCount = line.substring(endOfStack + 1);
                List<String> stackTraceElements = STACKTRACE_SPLITTER.splitToList(stackTrace);
                List<String> sanitizedStackElements = sanitizeFunction.map(stackTraceElements);
                if (!sanitizedStackElements.isEmpty()) {
                    sb.setLength(0);
                    STACKTRACE_JOINER.appendTo(sb, sanitizedStackElements);
                    sb.append(" ");
                    sb.append(invocationCount);
                    sb.append("\n");
                    writer.write(sb.toString());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface SanitizeFunction {
        List<String> map(List<String> stack);
    }

    private static class CompositeSanitizeFunction implements SanitizeFunction {

        private final List<SanitizeFunction> sanitizeFunctions;

        private CompositeSanitizeFunction(SanitizeFunction... sanitizeFunctions) {
            this(Arrays.asList(sanitizeFunctions));
        }

        private CompositeSanitizeFunction(Collection<SanitizeFunction> sanitizeFunctions) {
            this.sanitizeFunctions = ImmutableList.copyOf(sanitizeFunctions);
        }

        @Override
        public List<String> map(List<String> stack) {
            List<String> result = stack;
            for (SanitizeFunction sanitizeFunction : sanitizeFunctions) {
                result = sanitizeFunction.map(result);
            }
            return result;
        }
    }

    private static abstract class FrameWiseSanitizeFunction implements SanitizeFunction {
        @Override
        public final List<String> map(List<String> stack) {
            List<String> result = new ArrayList<>(stack.size());
            for (String frame : stack) {
                result.add(mapFrame(frame));
            }
            return result;
        }

        protected abstract String mapFrame(String frame);
    }

    private static class ReplaceContainment extends FrameWiseSanitizeFunction {
        private final Collection<String> keyWords;
        private final String replacement;

        private ReplaceContainment(Collection<String> keyWords, String replacement) {
            this.keyWords = keyWords;
            this.replacement = replacement;
        }

        @Override
        protected String mapFrame(String frame) {
            for (String keyWord : keyWords) {
                if (frame.contains(keyWord)) {
                    return replacement;
                }
            }
            return frame;
        }
    }

    private static class ReplaceRegex extends FrameWiseSanitizeFunction {
        private final Map<Pattern, String> replacements;

        private ReplaceRegex(Map<Pattern, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        protected String mapFrame(String frame) {
            for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
                Matcher matcher = replacement.getKey().matcher(frame);
                String value = replacement.getValue();
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, value);
                }
                matcher.appendTail(sb);
                if (sb.length() > 0) {
                    frame = sb.toString();
                }
            }
            return frame;
        }
    }

    private static class CollapseDuplicateFrames implements SanitizeFunction {
        @Override
        public List<String> map(List<String> stack) {
            List<String> result = new ArrayList<>(stack);
            ListIterator<String> iterator = result.listIterator();
            String previous = null;
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (next.equals(previous)) {
                    iterator.remove();
                }
                previous = next;
            }
            return result;
        }
    }

    private static class ChopPrefix implements SanitizeFunction {
        private final String stopToken;

        private ChopPrefix(String stopToken) {
            this.stopToken = stopToken;
        }

        @Override
        public List<String> map(List<String> stack) {
            for (int i = 0; i < stack.size(); i++) {
                String frame = stack.get(i);
                if (frame.contains(stopToken)) {
                    return stack.subList(i, stack.size());
                }
            }
            return stack;
        }
    }

    private static class NormalizeLambda extends FrameWiseSanitizeFunction {

        private static final Pattern LAMBDA_PATTERN = Pattern.compile(Pattern.quote("$$Lambda$") + "[0-9]+[./][0-9]+(?:x[0-9a-fA-F]+)?");

        @Override
        protected String mapFrame(String frame) {
            // Lambdas contain a name that's based on an index + timestamp or memory address at runtime and changes build-to-build.
            // This makes comparing two builds very difficult when a lambda is in the stack
            return LAMBDA_PATTERN.matcher(frame).replaceFirst("\\$\\$Lambda\\$");
        }
    }

    private static class ToSimpleName extends FrameWiseSanitizeFunction {

        @Override
        protected String mapFrame(String frame) {
            int firstUpper = CharMatcher.javaUpperCase().indexIn(frame);
            return frame.substring(Math.max(firstUpper, 0));
        }
    }
}
