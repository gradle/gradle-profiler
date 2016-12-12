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
package net.rubygrapefruit.gradle.profiler.hp;

import net.rubygrapefruit.gradle.profiler.ProfilerController;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;

public class HonestProfilerControl implements ProfilerController {
    private static final String PROFILE_HPL = "profile.hpl";

    private final HonestProfilerArgs args;
    private final File outputDir;

    public HonestProfilerControl(final HonestProfilerArgs args, final File outputDir) {
        this.args = args;
        this.outputDir = outputDir;
    }

    @Override
    public void start() throws IOException, InterruptedException {
        System.out.println("Starting profiling with Honest Profiler on port " + args.getPort());
        sendCommand("start");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        System.out.println("Stopping profiling with Honest Profiler on port " + args.getPort());
        sendCommand("stop");
        Files.copy(args.getLogPath().toPath(), new File(outputDir, PROFILE_HPL).toPath());
    }

    private void sendCommand(String command) throws IOException {
        Socket socket = new Socket("localhost", args.getPort());
        try (OutputStream out = socket.getOutputStream()) {
            out.write(commandBytes(command));
        }
    }

    private byte[] commandBytes(final String command) {
        return (command + "\r\n").getBytes();
    }

}
