/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.profiler.fg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlameGraphSanitizer {
    private final SanitizeFunction sanitizeFunction;

    public FlameGraphSanitizer(SanitizeFunction sanitizeFunction) {
        this.sanitizeFunction = sanitizeFunction;
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
                    String[] data = line.replaceAll(", ", ",").split(" ");
                    if (data.length == 2) {
                        String stackTraces = data[0];
                        String suffix = data[1];
                        String[] stackTraceElements = stackTraces.split(";");
                        List<String> remapped = new ArrayList<String>(stackTraceElements.length);
                        for (String stackTraceElement : stackTraceElements) {
                            String mapped = sanitizeFunction.map(stackTraceElement);
                            if (mapped != null) {
                                remapped.add(mapped);
                            }
                        }
                        if (!remapped.isEmpty()) {
                            sb.setLength(0);
                            StringJoiner joiner = new StringJoiner(";");
                            for (String s : remapped) {
                                joiner.add(s);
                            }
                            sb.append(joiner);
                            sb.append(' ');
                            sb.append(suffix);
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
        boolean skipLine(String line);

        String map(String entry);
    }

    public static final Map<Pattern, String> DEFAULT_REPLACEMENTS = Collections.unmodifiableMap(
        new LinkedHashMap<Pattern, String>() { {
            put(Pattern.compile("build_([a-z0-9]+)"), "build_");
            put(Pattern.compile("settings_([a-z0-9]+)"), "settings_");
            put(Pattern.compile("org[.]gradle[.]"), "");
            put(Pattern.compile("sun[.]reflect[.]GeneratedMethodAccessor[0-9]+"), "GeneratedMethodAccessor");
        }}
    );

    public static final SanitizeFunction DEFAULT_SANITIZE_FUNCTION = new RegexBasedSanitizerFunction( DEFAULT_REPLACEMENTS );

    public static class RegexBasedSanitizerFunction implements SanitizeFunction {
        private final Map<Pattern, String> replacements;

        public RegexBasedSanitizerFunction(Map<Pattern, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        public boolean skipLine(String line) {
            return false;
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

    }
}
