// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInsight.documentation

import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.action.GradleAttachSourcesProvider
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleDocumentationDownloader : DocumentationDownloader {

  override suspend fun canHandle(project: Project, file: VirtualFile): Boolean {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return false
    }
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      return false
    }
    if (readAction { findLibraryEntriesForFile(file, project).isEmpty() }) {
      return false
    }
    return readAction { JavaEditorFileSwapper.findSourceFile(project, file) == null }
  }

  override fun download(project: Project, file: VirtualFile): ActionCallback {
    val result = ActionCallback()
    GradleCoroutineScopeProvider.getInstance(project).cs
      .launch {
        val libraryEntries = readAction {
          findLibraryEntriesForFile(file, project)
        }
        if (libraryEntries.isEmpty()) {
          result.setRejected()
          return@launch
        }
        val action = readAction {
          val psiFile = file.findPsiFile(project)
          if (psiFile == null) {
            return@readAction null
          }
          val actions = GradleAttachSourcesProvider()
            .getActions(libraryEntries, psiFile)
          return@readAction actions.firstOrNull()
        }
        if (action == null) {
          result.setRejected()
          return@launch
        }
        action.perform(libraryEntries).apply {
          doWhenDone {
            if (isDone) {
              result.setDone()
            }
            else {
              result.setRejected()
            }
          }
        }
      }
    return result
  }

  private fun findLibraryEntriesForFile(file: VirtualFile, project: Project): List<LibraryOrderEntry> {
    val entries = mutableListOf<LibraryOrderEntry>()
    ProjectFileIndex.getInstance(project).getOrderEntriesForFile(file)
      .forEach {
        if (it is LibraryOrderEntry) {
          entries.add(it)
        }
      }
    return entries
  }
}