package org.gradle.profiler.studio.plugin;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class AndroidStudioSync2 {

    public static void main(String[] args) {
        new TestClass().setUp();
        System.out.println("HERE");
    }

    public static class TestClass extends BasePlatformTestCase {

        public void setUp() {
            try {
                super.setUp();
//                ExternalSystemUtil.refreshProject(
//                    getProject(),
//                    new ProjectSystemId("GRADLE"),
//                    "/Users/asodja/workspace/santa-tracker-android",
//                    new ExternalProjectRefreshCallback() {
//                        @Override
//                        public void onSuccess(@NotNull ExternalSystemTaskId externalTaskId, @Nullable DataNode<ProjectData> externalProject) {
//                            ExternalProjectRefreshCallback.super.onSuccess(externalTaskId, externalProject);
//                        }
//                    },
//                    false,
//                    ProgressExecutionMode.MODAL_SYNC,
//                    true
//                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}
