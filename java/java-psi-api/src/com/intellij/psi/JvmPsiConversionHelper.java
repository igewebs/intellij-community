// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmTypeDeclaration;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface JvmPsiConversionHelper {
  static @NotNull JvmPsiConversionHelper getInstance(@NotNull Project project) {
    return project.getService(JvmPsiConversionHelper.class);
  }

  @Nullable
  PsiClass convertTypeDeclaration(@Nullable JvmTypeDeclaration typeDeclaration);

  @NotNull
  PsiTypeParameter convertTypeParameter(@NotNull JvmTypeParameter typeParameter);

  @NotNull
  PsiType convertType(@NotNull JvmType type);

  @NotNull
  PsiSubstitutor convertSubstitutor(@NotNull JvmSubstitutor substitutor);

  @NotNull
  PsiMethod convertMethod(@NotNull JvmMethod method);
}
