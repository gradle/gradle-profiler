package org.gradle.profiler.studio.plugin.starter;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.ProjectManager;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * A custom starter that runs in headless mode, used mostly for tests running on CI/CD.
 */
public class HeadlessApplicationStarter implements ApplicationStarter {

    @Override
    public int getRequiredModality() {
        return NOT_IN_EDT;
    }

    @Nullable
    @Override
    public String getCommandName() {
        return "headless-starter";
    }

    @Override
    public boolean isHeadless() {
        return true;
    }

    @Override
    public void main(@NotNull List<String> args) {
        try {
            ApplicationEx app = ApplicationManagerEx.getApplicationEx();
            AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
            lifecyclePublisher.appFrameCreated(args);
            ProjectManager.getInstance().loadAndOpenProject(args.get(1));
        } catch (IOException | JDOMException e) {
            throw new RuntimeException(e);
        }
    }
}
