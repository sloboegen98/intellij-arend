package org.arend

import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager

class ArendDebuggerRunner: GenericDebuggerRunner() {
    override fun getRunnerId(): String {
        return "ArendDebugRunner"
    }

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return true
    }

    /*override fun createContentDescriptor(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val javaParameters = JavaParameters()
        javaParameters.jdk = ProjectRootManager.getInstance(environment.project).projectSdk
        val remoteConnection = DebuggerManagerImpl.createDebugParameters(javaParameters,
                true,
                DebuggerSettings.SOCKET_TRANSPORT,
                "",
                false)
        return attachVirtualMachine(state, environment, remoteConnection,
                //RemoteConnection(true, "127.0.0.1", "0", true),
                true)
    } */
}