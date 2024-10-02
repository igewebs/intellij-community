// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType

class PythonExistingEnvironmentSelector(model: PythonAddInterpreterModel) : PythonExistingEnvironmentConfigurator(model) {

  private lateinit var comboBox: PythonInterpreterComboBox

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.python.path")) {
        comboBox = pythonInterpreterComboBox(model.state.selectedInterpreter,
                                             model,
                                             model::addInterpreter,
                                             model.interpreterLoading)
          .align(Align.FILL)
          .component
      }
    }
  }

  override fun onShown() {
    comboBox.setItems(model.allInterpreters)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): Result<Sdk> {
    // todo error handling, nullability issues
    return Result.success(setupSdkIfDetected(model.state.selectedInterpreter.get()!!, model.existingSdks)!!)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(InterpreterType.REGULAR,
                                     statisticsTarget,
                                     false,
                                     false,
                                     true,
                                     //presenter.projectLocationContext is WslContext,
                                     false, // todo fix for wsl
                                     InterpreterCreationMode.CUSTOM)
  }
}