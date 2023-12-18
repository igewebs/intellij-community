// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CorrectnessChecker {
  @RequiresBlockingContext
  fun checkSemantic(file: PsiFile,
                    suggestion: String,
                    offset: Int,
                    prefix: String): List<CorrectnessError>

  // TODO function to check semantic in parallel with timelimit
}