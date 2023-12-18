package com.intellij.platform.ml.impl.correctness.autoimport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ImportFixer {
  @RequiresReadLock
  @RequiresBackgroundThread
  @RequiresBlockingContext
  fun runAutoImport(file: PsiFile, editor: Editor, suggestionRange: TextRange)

  object EMPTY : ImportFixer {
    override fun runAutoImport(file: PsiFile, editor: Editor, suggestionRange: TextRange) {}
  }
}

@RequiresBlockingContext
@ApiStatus.Internal
fun ImportFixer.runAutoImportAsync(scope: CoroutineScope, file: PsiFile, editor: Editor, suggestionRange: TextRange) {
  val autoImportAction = {
    if (!DumbService.getInstance(file.project).isDumb) {
      runAutoImport(file, editor, suggestionRange)
    }
  }

  if (ApplicationManager.getApplication().isUnitTestMode) {
    autoImportAction()
  }
  else {
    scope.launch {
      readAction {
        blockingContextToIndicator(autoImportAction)
      }
    }
  }
}
