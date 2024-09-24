// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.TaskInfoEntity
import com.intellij.platform.ide.progress.TaskManager
import com.intellij.platform.ide.progress.activeTasks
import com.intellij.platform.ide.progress.updates
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.projectId
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.collect
import fleet.kernel.rete.filter
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class TaskInfoEntityCollector(cs: CoroutineScope) {
  init {
    collectActiveTasks(cs, project = null)
  }
}

private class PerProjectTaskInfoEntityCollector(project: Project, cs: CoroutineScope) {
  init {
    collectActiveTasks(cs, project)
  }
}

private fun collectActiveTasks(cs: CoroutineScope, project: Project?) {
  cs.launch {
    val projectOrDefault = project ?: serviceAsync<ProjectManager>().defaultProject
    withKernel {
      activeTasks
        .filter { it.projectEntity?.projectId == project?.projectId() }
        .collect { task ->
          if (!isRhizomeProgressEnabled) return@collect

          showTaskIndicator(cs, projectOrDefault, task)
        }
    }
  }
}

private fun showTaskIndicator(cs: CoroutineScope, project: Project, task: TaskInfoEntity) {
  cs.launch {
    withKernel {
      tryWithEntities(task) {
        showIndicator(
          project,
          taskCancellingIndicator(this, task),
          taskInfo(task.title, task.cancellation),
          task.updates.asValuesFlow()
        )
      }
    }
  }
}

private fun taskCancellingIndicator(cs: CoroutineScope, taskInfo: TaskInfoEntity): ProgressIndicatorEx {
  val indicator = ProgressIndicatorBase()
  indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
    override fun cancel() {
      cs.launch {
        TaskManager.cancelTask(taskInfo)
      }
      super.cancel()
    }
  })
  return indicator
}