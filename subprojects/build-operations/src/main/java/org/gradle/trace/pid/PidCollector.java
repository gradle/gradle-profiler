package org.gradle.trace.pid;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.trace.stream.AsyncWriter;

import java.io.File;

@SuppressWarnings("unused")
public class PidCollector {
    public static void collect(GradleInternal gradle, File outFile) {
        ProcessEnvironment processEnvironment = gradle.getServices().get(ProcessEnvironment.class);
        AsyncWriter<Long> writer = new AsyncWriter<>(outFile, (l, w) -> w.print(l));
        writer.append(processEnvironment.getPid());
        writer.finished();
        gradle.addBuildListener(new BuildAdapter(){
            @Override
            public void buildFinished(BuildResult result) {
                writer.stop();
            }
        });
    }
}
