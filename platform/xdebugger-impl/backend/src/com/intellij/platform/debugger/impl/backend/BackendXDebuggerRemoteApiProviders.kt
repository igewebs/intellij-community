// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import fleet.rpc.remoteApiDescriptor

private class BackendXDebuggerRemoteApiProviders : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>()) {
      BackendXDebuggerValueLookupHintsRemoteApi()
    }
    remoteApi(remoteApiDescriptor<XDebuggerEvaluatorApi>()) {
      BackendXDebuggerEvaluatorApi()
    }
  }
}