// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.steps;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.intellij.pycharm.community.ide.impl.newProject.welcome.PyWelcomeGenerator;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.remote.PyProjectSynchronizer;
import com.jetbrains.python.sdk.PySdkExtKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PythonBaseProjectGenerator extends PythonProjectGenerator<PyNewProjectSettings> {
  public PythonBaseProjectGenerator() {
    super(true);
  }

  @NotNull
  @Nls
  @Override
  public String getName() {
    return PyBundle.message("pure.python.project");
  }

  @Override
  public @NotNull JPanel extendBasePanel() throws ProcessCanceledException {
    final JPanel panel = new JPanel(new VerticalFlowLayout(3, 0));
    panel.add(PyWelcomeGenerator.INSTANCE.createWelcomeSettingsPanel());
    return panel;
  }

  @Override
  public @NotNull Icon getLogo() {
    return PythonPsiApiIcons.Python;
  }

  @Override
  public void configureProject(@NotNull final Project project, @NotNull VirtualFile baseDir, @NotNull final PyNewProjectSettings settings,
                               @NotNull final Module module, @Nullable final PyProjectSynchronizer synchronizer) {
    // Super should be called according to its contract unless we sync project explicitly (we do not, so we call super)
    super.configureProject(project, baseDir, settings, module, synchronizer);
    PySdkExtKt.setPythonSdk(module, settings.getSdk());
    PyWelcomeGenerator.INSTANCE.welcomeUser(project, baseDir, module);
  }

  @Override
  public @NotNull String getNewProjectPrefix() {
    return "pythonProject";
  }

  @Override
  public boolean supportsWelcomeScript() {
    return true;
  }
}
