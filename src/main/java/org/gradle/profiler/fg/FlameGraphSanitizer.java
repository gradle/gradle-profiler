package org.gradle.profiler.fg;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

/**
 * Simplifies stacks to make flame graphs more readable.
 */
public class FlameGraphSanitizer {
    private static final Splitter STACKTRACE_SPLITTER = Splitter.on(";").omitEmptyStrings();
    private static final Joiner STACKTRACE_JOINER = Joiner.on(";");

    private final SanitizeFunction sanitizeFunction;

    public FlameGraphSanitizer(SanitizeFunction... sanitizeFunctions) {
        this.sanitizeFunction = new CompositeSanitizeFunction(sanitizeFunctions);
    }

    public void sanitize(final File in, File out) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(in)))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (sanitizeFunction.skipLine(line)) {
                        continue;
                    }
                    int endOfStack = line.lastIndexOf(" ");
                    if (endOfStack > 0) {
                        String stackTrace = line.substring(0, endOfStack);
                        String invocationCount = line.substring(endOfStack + 1);
                        List<String> stackTraceElements = STACKTRACE_SPLITTER.splitToList(stackTrace);
                        List<String> sanitizedStackElements = new ArrayList<String>(stackTraceElements.size());
                        for (String stackTraceElement : stackTraceElements) {
                            String sanitizedStackElement = sanitizeFunction.map(stackTraceElement);
                            if (sanitizedStackElement != null) {
                                String previousStackElement = Iterables.getLast(sanitizedStackElements, null);
                                if (!sanitizedStackElement.equals(previousStackElement)) {
                                    sanitizedStackElements.add(sanitizedStackElement);
                                }
                            }
                        }
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
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public interface SanitizeFunction {
        SanitizeFunction COLLAPSE_BUILD_SCRIPTS = new FlameGraphSanitizer.RegexBasedSanitizeFunction(
                ImmutableMap.of(
                        Pattern.compile("build_[a-z0-9]+"), "build script",
                        Pattern.compile("settings_[a-z0-9]+"), "settings script"
                )
        );
        SanitizeFunction COLLAPSE_GRADLE_INFRASTRUCTURE = new FlameGraphSanitizer.ContainmentBasedSanitizeFunction(
                ImmutableMap.of(
                        asList("BuildOperation"), "build operations",
                        asList("PluginManager", "ObjectConfigurationAction", "PluginTarget", "PluginAware", "Script.apply", "ScriptPlugin", "ScriptTarget", "ScriptRunner"), "plugin management",
                        asList("DynamicObject", "Closure.call", "MetaClass", "MetaMethod", "CallSite", "ConfigureDelegate", "Method.invoke", "MethodAccessor", "Proxy", "ConfigureUtil", "Script.invoke", "ClosureBackedAction", "getProperty("), "dynamic invocation",
                        asList("ProjectEvaluator", "Project.evaluate"), "project evaluation",
                        asList("CommandLine", "Executer", "Executor", "Execution", "Runner", "BuildController", "Bootstrap", "EntryPoint", "Main"), "execution infrastructure"
                )
        );

        String map(String entry);

        boolean skipLine(String line);
    }

    private static class CompositeSanitizeFunction implements SanitizeFunction {

        private final List<SanitizeFunction> sanitizeFunctions;

        private CompositeSanitizeFunction(SanitizeFunction... sanitizeFunctions) {
            this.sanitizeFunctions = ImmutableList.copyOf(sanitizeFunctions);
        }

        @Override
        public String map(String entry) {
            String result = entry;
            for (SanitizeFunction sanitizeFunction : sanitizeFunctions) {
                result = sanitizeFunction.map(result);
            }
            return result;
        }

        @Override
        public boolean skipLine(String line) {
            for (SanitizeFunction sanitizeFunction : sanitizeFunctions) {
                if (sanitizeFunction.skipLine(line)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ContainmentBasedSanitizeFunction implements SanitizeFunction {
        private final Map<String, String> replacements;

        public ContainmentBasedSanitizeFunction(Map<List<String>, String> replacements) {
            this.replacements = Maps.newHashMap();
            for (Map.Entry<List<String>, String> entry : replacements.entrySet()) {
                for (String key : entry.getKey()) {
                    this.replacements.put(key, entry.getValue());
                }
            }
        }

        @Override
        public String map(String entry) {
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                if (entry.contains(replacement.getKey())) {
                    return replacement.getValue();
                }
            }
            return entry;
        }

        @Override
        public boolean skipLine(String line) {
            return false;
        }
    }

    public static class RegexBasedSanitizeFunction implements SanitizeFunction {
        private final Map<Pattern, String> replacements;

        public RegexBasedSanitizeFunction(Map<Pattern, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        public String map(String entry) {
            for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
                Matcher matcher = replacement.getKey().matcher(entry);
                String value = replacement.getValue();
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, value);
                }
                matcher.appendTail(sb);
                if (sb.length() > 0) {
                    entry = sb.toString();
                }
            }
            return entry;
        }

        @Override
        public boolean skipLine(String line) {
            return false;
        }
    }
}
