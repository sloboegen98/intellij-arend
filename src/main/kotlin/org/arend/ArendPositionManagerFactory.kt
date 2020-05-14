package org.arend

import com.intellij.debugger.PositionManager
import com.intellij.debugger.PositionManagerFactory
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl

class ArendPositionManagerFactory: PositionManagerFactory() {
    override fun createPositionManager(process: DebugProcess): PositionManager? {
        println("Position Manager entry")
        return (process as? DebugProcessImpl)?.let { PositionManagerImpl(it) }
    }

}