// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath

fun EelFileSystemApi.userHomeBlocking(): EelPath.Absolute? {
  return runBlockingMaybeCancellable {
    userHome()
  }
}