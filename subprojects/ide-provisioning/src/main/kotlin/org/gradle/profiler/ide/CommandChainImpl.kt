package org.gradle.profiler.ide

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode

class CommandChainImpl(private val context: IDETestContext) : CommandChain {

    private var commandChain = com.intellij.tools.ide.performanceTesting.commands.CommandChain()

    override fun importGradleProject(): CommandChain {
        commandChain = commandChain.importGradleProject()
        return this
    }

    override fun waitForSmartMode(): CommandChain {
        commandChain = commandChain.waitForSmartMode()
        return this
    }

    override fun exitApp(): CommandChain {
        commandChain = commandChain.exitApp()
        return this
    }

    override fun run() {
        context.runIDE(commands = commandChain)
    }
}
