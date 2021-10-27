package org.gradle.profiler.studio.plugin;

import com.intellij.idea.IdeStarter;

public class HeadlessStarter extends IdeStarter {

    @Override
    public boolean isHeadless() {
        System.out.println("HELLO");
        return true;
    }
}
