// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class ImportSpecBuilder {

  private final @NotNull Project myProject;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private @NotNull ProgressExecutionMode myProgressExecutionMode;
  private @Nullable ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean isActivateBuildToolWindowOnStart = false;
  private boolean isActivateBuildToolWindowOnFailure = true;
  private @NotNull ThreeState isNavigateToError = ThreeState.UNSURE;
  private @Nullable String myVmOptions;
  private @Nullable String myArguments;
  private boolean myCreateDirectoriesForEmptyContentRoots;
  private @Nullable ProjectResolverPolicy myProjectResolverPolicy;
  private @Nullable UserDataHolderBase myUserData;

  public ImportSpecBuilder(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC;
  }

  public ImportSpecBuilder(ImportSpec importSpec) {
    this(importSpec.getProject(), importSpec.getExternalSystemId());
    apply(importSpec);
  }

  public ImportSpecBuilder use(@NotNull ProgressExecutionMode executionMode) {
    myProgressExecutionMode = executionMode;
    return this;
  }

  /**
   * @deprecated it does nothing from
   * 16.02.2017, 16:42, ebef09cdbbd6ace3c79d3e4fb63028bac2f15f75
   */
  @Deprecated(forRemoval = true)
  public ImportSpecBuilder forceWhenUptodate(boolean force) {
    return this;
  }

  public ImportSpecBuilder callback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
    return this;
  }

  public ImportSpecBuilder usePreviewMode() {
    isPreviewMode = true;
    return this;
  }

  public ImportSpecBuilder createDirectoriesForEmptyContentRoots() {
    myCreateDirectoriesForEmptyContentRoots = true;
    return this;
  }

  public ImportSpecBuilder activateBuildToolWindowOnStart() {
    isActivateBuildToolWindowOnStart = true;
    return this;
  }

  public ImportSpecBuilder dontReportRefreshErrors() {
    isActivateBuildToolWindowOnFailure = false;
    return this;
  }

  public ImportSpecBuilder dontNavigateToError() {
    isNavigateToError = ThreeState.NO;
    return this;
  }

  public ImportSpecBuilder navigateToError() {
    isNavigateToError = ThreeState.YES;
    return this;
  }

  public ImportSpecBuilder withVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
    return this;
  }

  public ImportSpecBuilder withArguments(@Nullable String arguments) {
    myArguments = arguments;
    return this;
  }

  @ApiStatus.Experimental
  public ImportSpecBuilder projectResolverPolicy(@NotNull ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
    return this;
  }

  public ImportSpecBuilder withUserData(@Nullable UserDataHolderBase userData) {
    myUserData = userData;
    return this;
  }

  public ImportSpec build() {
    ImportSpecImpl mySpec = new ImportSpecImpl(myProject, myExternalSystemId);
    mySpec.setProgressExecutionMode(myProgressExecutionMode);
    mySpec.setCreateDirectoriesForEmptyContentRoots(myCreateDirectoriesForEmptyContentRoots);
    mySpec.setPreviewMode(isPreviewMode);
    mySpec.setActivateBuildToolWindowOnStart(isActivateBuildToolWindowOnStart);
    mySpec.setActivateBuildToolWindowOnFailure(isActivateBuildToolWindowOnFailure);
    mySpec.setNavigateToError(isNavigateToError);
    mySpec.setArguments(myArguments);
    mySpec.setVmOptions(myVmOptions);
    mySpec.setProjectResolverPolicy(myProjectResolverPolicy);
    mySpec.setUserData(myUserData);
    ExternalProjectRefreshCallback callback;
    if (myCallback != null) {
      callback = myCallback;
    }
    else if (myProjectResolverPolicy == null || !myProjectResolverPolicy.isPartialDataResolveAllowed()) {
      callback = new DefaultProjectRefreshCallback(mySpec);
    }
    else {
      callback = null;
    }
    mySpec.setCallback(callback);
    return mySpec;
  }

  private void apply(ImportSpec spec) {
    myProgressExecutionMode = spec.getProgressExecutionMode();
    myCreateDirectoriesForEmptyContentRoots = spec.shouldCreateDirectoriesForEmptyContentRoots();
    myCallback = spec.getCallback();
    isPreviewMode = spec.isPreviewMode();
    isActivateBuildToolWindowOnStart = spec.isActivateBuildToolWindowOnStart();
    isActivateBuildToolWindowOnFailure = spec.isActivateBuildToolWindowOnFailure();
    myArguments = spec.getArguments();
    myVmOptions = spec.getVmOptions();
    myUserData = spec.getUserData();
  }

  @ApiStatus.Internal
  public static final class DefaultProjectRefreshCallback implements ExternalProjectRefreshCallback {
    private final Project myProject;

    public DefaultProjectRefreshCallback(ImportSpec spec) {
      myProject = spec.getProject();
    }

    @Override
    public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
      if (externalProject == null) {
        return;
      }
      ProjectDataManager.getInstance().importData(externalProject, myProject);
    }
  }
}
