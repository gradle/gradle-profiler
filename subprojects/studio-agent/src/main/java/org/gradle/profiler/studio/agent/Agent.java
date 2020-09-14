package org.gradle.profiler.studio.agent;

import org.gradle.profiler.client.protocol.Client;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

public class Agent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("PROFILER AGENT RUNNING");

        int port = Integer.parseInt(agentArgs);
        try {
            Client.INSTANCE.connect(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("CONNECTED TO CONTROLLER PROCESS");
    }
}
