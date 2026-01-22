package org.gradle.profiler.flamegraph;

import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.ItemToolkit;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrAttributes;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator.FrameCategorization;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceFormatToolkit;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

/**
 * Converts JFR recordings to the collapsed stacks format used by the FlameGraph tool.
 */
public class JfrToStacksConverter {
    private final Map<DetailLevel, FlameGraphSanitizer> sanitizers;

    public JfrToStacksConverter(Map<DetailLevel, FlameGraphSanitizer> sanitizers) {
        this.sanitizers = sanitizers;
    }

    public List<Stacks> generateStacks(File jfrFile, String outputBaseName) {
        Stream<File> jfrFiles = jfrFile.isDirectory()
            ? Stream.of(requireNonNull(jfrFile.listFiles((dir, name) -> name.endsWith(".jfr"))))
            : Stream.of(jfrFile);
        List<IItemCollection> recordings = jfrFiles
            .map(file -> {
                try {
                    return JfrLoaderToolkit.loadEvents(file);
                } catch (IOException | CouldNotLoadRecordingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

        List<Stacks> stacks = new ArrayList<>();
        try {
            for (EventType type : EventType.values()) {
                for (DetailLevel level : DetailLevel.values()) {
                    String eventFileBaseName = outputBaseName + Stacks.postFixFor(type, level);
                    File stacksFile = generateStacks(jfrFile.getParentFile(), eventFileBaseName, recordings, type, level);
                    if (stacksFile != null) {
                        stacks.add(new Stacks(stacksFile, type, level, eventFileBaseName));
                    }
                }
            }
            return stacks;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private File generateStacks(File baseDir, String eventFileBaseName, List<IItemCollection> recordings, EventType type, DetailLevel level) throws IOException {
        File stacks = File.createTempFile("stacks", ".txt");
        convertToStacks(recordings, stacks, new Options(type, level.isShowArguments(), level.isShowLineNumbers()));
        if (stacks.length() == 0) {
            stacks.delete();
            return null;
        }
        File sanitizedStacks = new File(baseDir, eventFileBaseName + Stacks.STACKS_FILE_SUFFIX);
        sanitizers.get(level).sanitize(stacks, sanitizedStacks);
        stacks.delete();
        return sanitizedStacks;
    }

    private void convertToStacks(List<IItemCollection> recordings, File targetFile, Options options) {
        Map<String, Long> foldedStacks = foldStacks(recordings, options);
        writeFoldedStacks(foldedStacks, targetFile);
    }

    private Map<String, Long> foldStacks(List<IItemCollection> recordings, Options options) {
        StackFolder folder = new StackFolder(options);
        recordings.stream()
            .flatMap(recording -> StreamSupport.stream(recording.spliterator(), false))
            .flatMap(eventStream -> StreamSupport.stream(eventStream.spliterator(), false))
            .filter(options.eventType::matches)
            .filter(event -> getStackTrace(event) != null)
            .forEach(folder);
        return folder.getFoldedStacks();
    }

    private void writeFoldedStacks(Map<String, Long> foldedStacks, File targetFile) {
        targetFile.getParentFile().mkdirs();
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath(), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Long> entry : foldedStacks.entrySet()) {
                writer.write(String.format("%s %d%n", entry.getKey(), entry.getValue()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static IMCStackTrace getStackTrace(IItem event) {
        return ItemToolkit.getItemType(event).getAccessor(JfrAttributes.EVENT_STACKTRACE.getKey()).getMember(event);
    }

    private static class StackFolder implements Consumer<IItem> {
        private final Options options;
        private final Map<String, Long> foldedStacks = new LinkedHashMap<>();

        public StackFolder(Options options) {
            this.options = options;
        }

        @Override
        public void accept(IItem event) {
            String stack = toStack(event);
            Long sum = foldedStacks.get(stack);
            long value = getValue(event);
            if (sum == null) {
                sum = value;
            } else {
                sum += value;
            }
            foldedStacks.put(stack, sum);
        }

        private String toStack(IItem event) {
            IMCStackTrace stackTrace = getStackTrace(event);
            List<IMCFrame> reverseStacks = new ArrayList<>(stackTrace.getFrames());
            Collections.reverse(reverseStacks);
            return reverseStacks.stream()
                .map(this::frameName)
                .collect(Collectors.joining(";"));
        }

        private String frameName(IMCFrame frame) {
            String frameName = StacktraceFormatToolkit.formatFrame(
                frame,
                new FrameSeparator(options.isShowLineNumbers() ? FrameCategorization.LINE : FrameCategorization.METHOD, false),
                false,
                false,
                true,
                true,
                options.isShowArguments(),
                true
            );
            return frame.getType() == IMCFrame.Type.UNKNOWN
                ? frameName
                : frameName + "_[j]";
        }

        private long getValue(IItem event) {
            return options.getEventType().getValue(event);
        }

        public Map<String, Long> getFoldedStacks() {
            return foldedStacks;
        }
    }

    public static class Options {
        private final EventType eventType;
        private final boolean showArguments;
        private final boolean showLineNumbers;

        public Options(EventType eventType, boolean showArguments, boolean showLineNumbers) {
            this.eventType = eventType;
            this.showArguments = showArguments;
            this.showLineNumbers = showLineNumbers;
        }

        public EventType getEventType() {
            return eventType;
        }

        public boolean isShowArguments() {
            return showArguments;
        }

        public boolean isShowLineNumbers() {
            return showLineNumbers;
        }
    }

}
