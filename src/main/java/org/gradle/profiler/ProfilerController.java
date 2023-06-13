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
package org.gradle.profiler;

import java.io.IOException;

public interface ProfilerController {
    ProfilerController EMPTY = new ProfilerController() {
        @Override
        public void startSession() {

        }

        @Override
        public void startRecording() {

        }

        @Override
        public void stopRecording(String pid) {

        }

        @Override
        public void stopSession() {

        }
    };

    /**
     * Connects the profiler to the daemon and does any other one-time setup work.
     * The profiler should not start collecting data yet. If the profiler cannot
     * connect without starting data collection, it should defer startup to {@link #startRecording()}
     * instead.
     */
    void startSession() throws IOException, InterruptedException;

    /**
     * Tells the profiler to start collecting data (again). Profilers may chose to throw an
     * exception if they don't support multiple start/stop operations.
     */
    void startRecording() throws IOException, InterruptedException;

    /**
     * Tells the profiler to stop collecting data for now, e.g. so it doesn't
     * profile cleanup tasks. If the data collection can only be stopped by
     * stopping the session, the profiler should implement this as a no-op
     * and throw an exception when {@link #startRecording()} is called another
     * time.
     */
    void stopRecording(String pid) throws IOException, InterruptedException;

    /**
     * Ends the profiling session, writing the collected results to disk
     * and disconnecting the profiler from the daemon.
     */
    void stopSession() throws IOException, InterruptedException;
}
