package org.gradle.profiler.ide

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContainerImpl

object Starter {
  fun newTestContainer(): TestContainer<*> {
    return TestContainerImpl()
  }

  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext =
    newTestContainer().initializeTestContext(testName = testName, testCase = testCase, preserveSystemDir = preserveSystemDir)
}
