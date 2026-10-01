package org.gradle.profiler.perfetto.jfr;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import perfetto.protos.Callstack;
import perfetto.protos.Frame;
import perfetto.protos.InternedData;
import perfetto.protos.InternedString;

/**
 * Deduplicates sampled stack traces into Perfetto's interned function, frame, and callstack tables.
 */
public final class PerfettoCallstackInterner {
    private final PerfettoIdProvider idProvider;
    private final Map<String, Long> functionIds = new HashMap<>();
    private final Map<FrameKey, Long> frameIds = new HashMap<>();
    private final Map<List<Long>, Long> callstackIds = new HashMap<>();

    public PerfettoCallstackInterner(PerfettoIdProvider idProvider) {
        this.idProvider = idProvider;
    }

    public InterningResult internCallstack(RecordedStackTrace stackTrace) {
        if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
            return new InterningResult(0L, null);
        }

        InternedData.Builder delta = null;
        List<Long> internedFrameIds = new ArrayList<>();
        List<RecordedFrame> frames = stackTrace.getFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            RecordedFrame frame = frames.get(index);
            if (frame == null) {
                continue;
            }

            String functionName = formatFunctionName(frame.getMethod());
            if (functionName == null || functionName.isBlank()) {
                continue;
            }

            Long functionId = functionIds.get(functionName);
            if (functionId == null) {
                functionId = idProvider.nextId();
                functionIds.put(functionName, functionId);
                if (delta == null) {
                    delta = InternedData.newBuilder();
                }
                delta.addFunctionNames(InternedString.newBuilder()
                    .setIid(functionId)
                    .setStr(ByteString.copyFromUtf8(functionName)));
            }

            FrameKey frameKey = new FrameKey(functionId, frame.getBytecodeIndex());
            Long frameId = frameIds.get(frameKey);
            if (frameId == null) {
                frameId = idProvider.nextId();
                frameIds.put(frameKey, frameId);
                if (delta == null) {
                    delta = InternedData.newBuilder();
                }
                delta.addFrames(Frame.newBuilder()
                    .setIid(frameId)
                    .setFunctionNameId(functionId)
                    .setRelPc(Math.max(frame.getBytecodeIndex(), 0)));
            }

            internedFrameIds.add(frameId);
        }

        if (internedFrameIds.isEmpty()) {
            return new InterningResult(0L, delta == null ? null : delta.build());
        }

        List<Long> callstackKey = List.copyOf(internedFrameIds);
        Long callstackId = callstackIds.get(callstackKey);
        if (callstackId == null) {
            callstackId = idProvider.nextId();
            callstackIds.put(callstackKey, callstackId);
            if (delta == null) {
                delta = InternedData.newBuilder();
            }
            Callstack.Builder callstack = Callstack.newBuilder().setIid(callstackId);
            for (Long frameId : callstackKey) {
                callstack.addFrameIds(frameId);
            }
            delta.addCallstacks(callstack);
        }

        return new InterningResult(callstackId, delta == null ? null : delta.build());
    }

    private static String formatFunctionName(RecordedMethod method) {
        if (method == null) {
            return null;
        }
        String typeName = method.getType() == null ? null : method.getType().getName();
        String methodName = method.getName();
        if (typeName == null || typeName.isBlank()) {
            return methodName;
        }
        if (methodName == null || methodName.isBlank()) {
            return typeName;
        }
        return typeName + "." + methodName;
    }

    public record InterningResult(long callstackIid, InternedData delta) {
    }

    private record FrameKey(long functionId, int bytecodeIndex) {
    }
}
