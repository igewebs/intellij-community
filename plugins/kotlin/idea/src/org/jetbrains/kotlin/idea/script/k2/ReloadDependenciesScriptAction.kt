// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.ConcurrentHashMap

fun KtFile.getScriptAnnotationsList(): List<String> = annotationEntries.map { it.text }.sorted()

internal class ReloadDependenciesScriptAction : AnAction() {
    val previousAnnotations = ConcurrentHashMap<VirtualFile, List<String>>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = getKotlinScriptFile(editor) ?: return

        runWithModalProgressBlocking(
            project,
            KotlinBaseScriptingBundle.message("progress.title.loading.script.dependencies")
        ) {
            CustomScriptDependenciesSource.getInstance(project)?.updateDependenciesAndCreateModules(
                listOf(BaseScriptModel(file))
            )

            previousAnnotations[file] = readAction {
                PsiManager.getInstance(project).findFile(file)?.safeAs<KtFile>()?.getScriptAnnotationsList() ?: emptyList()
            }

            EditorNotifications.getInstance(project).updateNotifications(file)
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        e.presentation.isEnabledAndVisible = getNotificationVisibility(editor)
    }

    private fun getNotificationVisibility(editor: Editor): Boolean {
        if (DiffUtil.isDiffEditor(editor)) return false

        val project = editor.project ?: return false
        val file = getKotlinScriptFile(editor) ?: return false

        val mainKts = MainKtsScriptDefinitionSource.getInstance(project)?.definitions?.singleOrNull() ?: return false

        if (!mainKts.isScript(VirtualFileScriptSource(file))) return false

        val actualAnnotations = PsiManager.getInstance(project).findFile(file)?.safeAs<KtFile>()?.getScriptAnnotationsList() ?: emptyList()

        val fileAnnotations = previousAnnotations[file] ?: emptyList()
        return fileAnnotations.isEmpty() || actualAnnotations != fileAnnotations
    }
}

private fun getKotlinScriptFile(editor: Editor): VirtualFile? = FileDocumentManager.getInstance()
    .getFile(editor.document)
    ?.takeIf {
        it !is LightVirtualFileBase
                && it.isValid
                && it.isKotlinFileType()
    }
