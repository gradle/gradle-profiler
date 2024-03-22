package org.gradle.profiler.ide

import com.intellij.ide.starter.ide.IDETestContext

class RunIdeContextImpl(private val context: IDETestContext) : RunIdeContext {
    override fun withSystemProperty(key: String, value: String): RunIdeContext {
        context.applyVMOptionsPatch {
            addSystemProperty(key, value)
        }
        return this
    }

    override fun withCommands(): CommandChain {
        return CommandChainImpl(context)
    }
}
