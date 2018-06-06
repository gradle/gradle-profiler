package org.gradle.profiler.fg;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.io.*;
import java.util.*;
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
    private static final String GRADLE_INFRASTRUCTURE = "Gradle infrastructure";

    public static final SanitizeFunction COLLAPSE_BUILD_SCRIPTS = new ReplaceRegex(
            ImmutableMap.of(
                    Pattern.compile("build_[a-z0-9]+"), "build script",
                    Pattern.compile("settings_[a-z0-9]+"), "settings script"
            )
    );

    public static final SanitizeFunction COLLAPSE_GRADLE_INFRASTRUCTURE = new CompositeSanitizeFunction(
            new ChopPrefix("DefaultGradleLauncher.loadSettings()"),
            new ChopPrefix("DefaultGradleLauncher.configureBuild()"),
            new ChopPrefix("DefaultGradleLauncher.constructTaskGraph()"),
            new ChopPrefix("DefaultGradleLauncher.executeTasks()"),
            new ChopPrefix("CatchExceptionTaskExecuter.execute(...)"),
            new ReplaceContainment(asList("DynamicObject", "Closure.call", "MetaClass", "MetaMethod", "CallSite", "ConfigureDelegate", "Method.invoke", "MethodAccessor", "Proxy", "ConfigureUtil", "Script.invoke", "ClosureBackedAction", "getProperty("), "dynamic invocation"),
            new ReplaceContainment(asList("BuildOperation", "PluginManager", "ObjectConfigurationAction", "PluginTarget", "PluginAware", "Script.apply", "ScriptPlugin", "ScriptTarget", "ScriptRunner", "ProjectEvaluator", "Project.evaluate"), GRADLE_INFRASTRUCTURE),
            new ReplaceContainment(singletonList("TaskExecuter"), "task executer")
    );

    private final SanitizeFunction sanitizeFunction;

    public FlameGraphSanitizer(SanitizeFunction... sanitizeFunctions) {
        ;
        ImmutableList<SanitizeFunction> functions = ImmutableList.<SanitizeFunction>builder()
                .addAll(Arrays.asList(sanitizeFunctions))
                .add(new CollapseDuplicateFrames())
                .build();
        this.sanitizeFunction = new CompositeSanitizeFunction(functions);
    }

    public void sanitize(final File in, File out) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(in)))) {
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
            List<String> result = Lists.newArrayListWithCapacity(stack.size());
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
            List<String> result = Lists.newArrayList(stack);
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
        private final String prefix;

        private ChopPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public List<String> map(List<String> stack) {
            int i = stack.indexOf(prefix);
            if (i >= 0) {
                return stack.subList(i, stack.size());
            } else {
                return stack;
            }
        }
    }
}
