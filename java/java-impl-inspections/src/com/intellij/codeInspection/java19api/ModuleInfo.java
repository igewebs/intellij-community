// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.psi.PsiJavaModule.JAVA_BASE;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

record ModuleInfo(@NotNull PsiDirectory rootDir,
                  @NotNull String name,
                  @NotNull Set<ModuleNode> requires,
                  @NotNull Set<String> exports) {
  boolean fileAlreadyExists() {
    return StreamEx.of(rootDir().getChildren())
      .select(PsiFile.class)
      .map(PsiFileSystemItem::getName)
      .anyMatch(MODULE_INFO_FILE::equals);
  }

  @NotNull
  CharSequence createModuleText() {
    CharSequence requires = requiresText();
    CharSequence exports = exportsText();

    return new StringBuilder().append(PsiKeyword.MODULE).append(" ").append(name()).append(" {\n")
      .append(requires)
      .append((!requires.isEmpty() && !exports.isEmpty()) ? "\n" : "")
      .append(exports)
      .append("}");
  }

  @NotNull
  private CharSequence requiresText() {
    StringBuilder text = new StringBuilder();
    for (ModuleNode dependency : requires()) {
      final String dependencyName = dependency.getName();
      if (JAVA_BASE.equals(dependencyName)) continue;
      boolean isBadSyntax = ContainerUtil.or(dependencyName.split("\\."),
                                             part -> JavaLexer.isKeyword(part, LanguageLevel.JDK_1_9));
      text.append(isBadSyntax ? "// " : " ").append(PsiKeyword.REQUIRES).append(' ').append(dependencyName).append(";\n");
    }
    return text;
  }

  @NotNull
  private CharSequence exportsText() {
    StringBuilder text = new StringBuilder();
    for (String packageName : exports()) {
      text.append(PsiKeyword.EXPORTS).append(' ').append(packageName).append(";\n");
    }
    return text;
  }
}
